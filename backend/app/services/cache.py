import hashlib
from cachetools import TTLCache

# 동일 메시지 24시간 캐시 (최대 1000개)
_cache: TTLCache = TTLCache(maxsize=1000, ttl=86400)


def make_key(masked_message: str, source_type: str) -> str:
    content = f"{source_type}:{masked_message}"
    return hashlib.sha256(content.encode()).hexdigest()


def get(key: str) -> dict | None:
    return _cache.get(key)


def set(key: str, value: dict) -> None:
    _cache[key] = value
