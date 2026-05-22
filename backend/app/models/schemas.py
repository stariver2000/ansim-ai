from pydantic import BaseModel, Field
from typing import Literal


class AnalyzeRequest(BaseModel):
    message: str = Field(..., min_length=1, max_length=5000)
    source_type: Literal["sms", "email", "kakao", "unknown"] = "unknown"


class PhishingResult(BaseModel):
    label: Literal["safe", "suspicious", "phishing"]
    risk_score: int = Field(..., ge=0, le=100)
    reasons: list[str]
    recommended_action: str
    detected_signals: list[str]
    analyzed_by: Literal["local", "gemini"] = "gemini"
