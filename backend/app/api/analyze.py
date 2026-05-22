import logging
from fastapi import APIRouter, HTTPException

from app.models.schemas import AnalyzeRequest, PhishingResult
from app.services.masker import mask_sensitive
from app.services.rule_score import local_risk_score
from app.services.gemini_client import analyze_with_gemini
from app.services import cache

router = APIRouter()
logger = logging.getLogger(__name__)

# 로컬 판정 임계값
LOCAL_SAFE_THRESHOLD = 30    # 미만: Gemini 호출 없이 safe 반환
LOCAL_DANGER_THRESHOLD = 70  # 이상: 즉시 위험 + Gemini 정밀 분석


@router.post("/analyze", response_model=PhishingResult)
async def analyze(req: AnalyzeRequest) -> PhishingResult:
    # 1단계: 로컬 규칙 점수화
    local_score, local_signals = local_risk_score(req.message)
    logger.info(f"로컬 점수: {local_score}, 신호: {local_signals}")

    # 2단계: 점수 낮으면 Gemini 호출 없이 즉시 반환
    if local_score < LOCAL_SAFE_THRESHOLD:
        return PhishingResult(
            label="safe",
            risk_score=local_score,
            reasons=["위험 신호가 낮습니다."],
            recommended_action="링크 클릭이나 개인정보 입력 전에는 공식 앱 또는 공식 채널에서 직접 확인하세요.",
            detected_signals=local_signals,
            analyzed_by="local"
        )

    # 3단계: 개인정보 마스킹
    masked = mask_sensitive(req.message)

    # 4단계: 캐시 확인
    cache_key = cache.make_key(masked, req.source_type)
    cached = cache.get(cache_key)
    if cached:
        logger.info("캐시 히트")
        return PhishingResult(**cached, analyzed_by="gemini")

    # 5단계: Gemini 분석
    try:
        result = analyze_with_gemini(
            masked_message=masked,
            local_score=local_score,
            local_signals=local_signals,
            source_type=req.source_type,
        )
    except Exception as e:
        logger.error(f"Gemini 오류: {e}")
        # Gemini 실패 시 로컬 결과로 폴백
        label = "phishing" if local_score >= LOCAL_DANGER_THRESHOLD else "suspicious"
        return PhishingResult(
            label=label,
            risk_score=local_score,
            reasons=["AI 분석 일시 불가 — 로컬 규칙 기반 결과입니다."],
            recommended_action="주의하세요. 공식 채널로 직접 확인하세요.",
            detected_signals=local_signals,
            analyzed_by="local"
        )

    # 6단계: 캐시 저장 & 반환
    cache.set(cache_key, result)
    return PhishingResult(**result, analyzed_by="gemini")
