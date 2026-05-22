import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.analyze import router as analyze_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)

app = FastAPI(
    title="안심동행 AI — 피싱 탐지 API",
    version="1.0.0",
    docs_url="/docs",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 배포 시 Android 앱 도메인으로 제한
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)

app.include_router(analyze_router, prefix="/api/v1")


@app.get("/health")
def health():
    return {"status": "ok"}
