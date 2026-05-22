import re


def mask_sensitive(text: str) -> str:
    """개인정보 마스킹 — Gemini 전송 전 반드시 적용"""

    # 전화번호
    text = re.sub(r"\b01[016789]-?\d{3,4}-?\d{4}\b", "[PHONE]", text)

    # 이메일
    text = re.sub(
        r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}",
        "[EMAIL]", text
    )

    # 주민등록번호
    text = re.sub(r"\b\d{6}-?\d{7}\b", "[ID_NUMBER]", text)

    # 계좌번호 (xx-xxxxx-xx 형태)
    text = re.sub(r"\b\d{2,6}-\d{2,6}-\d{2,8}\b", "[ACCOUNT]", text)

    # URL: 완전 삭제 대신 도메인만 남김 (피싱 탐지 핵심 단서)
    def replace_url(match: re.Match) -> str:
        url = match.group(0)
        domain = re.search(r"https?://([^/\s?#]+)", url)
        if domain:
            return f"[URL:{domain.group(1)}]"
        return "[URL]"

    text = re.sub(r"https?://\S+", replace_url, text)

    # 6자리 인증번호 (계좌번호 처리 후에 실행)
    text = re.sub(r"\b\d{6}\b", "[CODE]", text)

    return text
