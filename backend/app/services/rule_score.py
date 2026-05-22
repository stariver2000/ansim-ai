import re


# 의심 키워드 (5점씩)
SUSPICIOUS_KEYWORDS = [
    "긴급", "즉시", "오늘까지", "정지", "차단", "압류", "미납",
    "본인인증", "계정", "보안", "결제 실패", "만료", "일시정지",
    "택배", "배송", "카드", "은행", "검찰", "경찰", "국세청",
    "금감원", "금융감독원", "금융위원회",
    "대출", "환급", "지원금", "당첨", "무료",
]

# 고위험 키워드 (15점씩)
HIGH_RISK_KEYWORDS = [
    "비밀번호", "계좌", "송금", "입금", "출금",
    "주민등록번호", "신분증", "원격제어", "앱 설치",
    "개인정보", "안전계좌", "자산 보호",
]


def local_risk_score(message: str) -> tuple[int, list[str]]:
    """
    로컬 1차 위험도 점수 계산
    Returns: (score 0~100, detected_signals)
    """
    score = 0
    signals: list[str] = []

    # URL 포함
    if re.search(r"https?://\S+", message):
        score += 25
        signals.append("URL 포함")

    # 단축 URL
    if re.search(r"(bit\.ly|tinyurl|t\.co|shorturl|url\.kr|me2\.do)", message, re.IGNORECASE):
        score += 25
        signals.append("단축 URL 의심")

    # 의심 키워드
    for kw in SUSPICIOUS_KEYWORDS:
        if kw in message:
            score += 5
            signals.append(f"의심 키워드: {kw}")

    # 고위험 키워드
    for kw in HIGH_RISK_KEYWORDS:
        if kw in message:
            score += 15
            signals.append(f"고위험 키워드: {kw}")

    # 조합 패턴: 인증 + 링크
    if ("인증" in message or "확인" in message) and re.search(r"https?://", message):
        score += 20
        signals.append("인증·확인 + 링크 조합")

    # APK 설치 유도
    if re.search(r"(apk|앱\s*(설치|다운|받아|깔아))", message, re.IGNORECASE):
        score += 40
        signals.append("APK·앱 설치 유도")

    return min(score, 100), signals
