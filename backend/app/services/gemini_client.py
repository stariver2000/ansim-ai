import json
import logging
from google import genai
from google.genai import types

from app.core.config import settings

logger = logging.getLogger(__name__)

client = genai.Client(api_key=settings.gemini_api_key)

# Gemini 구조화 출력 스키마
PHISHING_SCHEMA = {
    "type": "object",
    "properties": {
        "label": {
            "type": "string",
            "enum": ["safe", "suspicious", "phishing"]
        },
        "risk_score": {"type": "integer"},
        "reasons": {
            "type": "array",
            "items": {"type": "string"}
        },
        "recommended_action": {"type": "string"},
        "detected_signals": {
            "type": "array",
            "items": {"type": "string"}
        }
    },
    "required": ["label", "risk_score", "reasons", "recommended_action", "detected_signals"]
}

SYSTEM_PROMPT = """너는 한국 금융사기·피싱 탐지 AI다.

판단 기준:
- safe: 위험 신호 없음 (일반 안내, 광고, 지인 연락 등)
- suspicious: 일부 의심 요소 있으나 피싱 단정 불가
- phishing: 피싱 가능성 높음

중점 판단 항목:
1. 기관 사칭 (검찰·경찰·금감원·은행·카드사 등)
2. 링크 클릭·앱 설치·개인정보 입력 유도
3. 계좌이체·송금·상품권 구매 요구
4. 긴급성·공포감 조성 ("계정 정지", "압류", "즉시" 등)
5. 수사기관이 절대 요구하지 않는 것들

규칙:
- reasons는 한국어로, 구체적으로 2~4개
- recommended_action은 사용자가 바로 이해할 수 있는 한 문장
- risk_score는 0~100 정수
- JSON만 반환, 설명 없이"""


def analyze_with_gemini(
    masked_message: str,
    local_score: int,
    local_signals: list[str],
    source_type: str
) -> dict:
    prompt = f"""[로컬 1차 점수: {local_score}/100]
[로컬 감지 신호: {', '.join(local_signals) if local_signals else '없음'}]
[출처: {source_type}]

분석 메시지:
{masked_message}"""

    response = client.models.generate_content(
        model=settings.gemini_model,
        contents=prompt,
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_PROMPT,
            temperature=0.0,
            max_output_tokens=400,
            response_mime_type="application/json",
            response_schema=PHISHING_SCHEMA,
        ),
    )

    result = json.loads(response.text)
    result["risk_score"] = max(0, min(100, int(result["risk_score"])))
    logger.info(f"Gemini 분석 완료: label={result['label']}, score={result['risk_score']}")
    return result
