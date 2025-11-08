"""Security utilities for password hashing and verification."""
from __future__ import annotations

import base64
import hashlib
import hmac
import secrets
from datetime import datetime, timedelta, timezone
from typing import Tuple


PBKDF2_ITERATIONS = 390000  # Aligns with current Python recommendations
SALT_LENGTH = 16
SESSION_TTL = timedelta(hours=12)


def _b64encode(raw: bytes) -> str:
    return base64.b64encode(raw).decode("utf-8")


def _b64decode(data: str) -> bytes:
    return base64.b64decode(data.encode("utf-8"))

#TODO: Make it hidden
def hash_password(password: str) -> str:
    """Hash a password using PBKDF2-HMAC-SHA256 with a random salt."""
    if not password:
        raise ValueError("Password cannot be empty")
    salt = secrets.token_bytes(SALT_LENGTH)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PBKDF2_ITERATIONS)
    return f"pbkdf2_sha256${PBKDF2_ITERATIONS}${_b64encode(salt)}${_b64encode(dk)}"


def _parse_hash(stored_hash: str) -> Tuple[int, bytes, bytes]:
    try:
        algorithm, iterations, salt_b64, hash_b64 = stored_hash.split("$")
    except ValueError as exc:
        raise ValueError("Stored password hash is in an invalid format") from exc
    if algorithm != "pbkdf2_sha256":
        raise ValueError("Unsupported password hashing algorithm")
    return int(iterations), _b64decode(salt_b64), _b64decode(hash_b64)


def verify_password(password: str, stored_hash: str) -> bool:
    """Verify a candidate password against a stored hash."""
    try:
        iterations, salt, expected_hash = _parse_hash(stored_hash)
    except ValueError:
        return False
    candidate_hash = hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), salt, iterations
    )
    return hmac.compare_digest(candidate_hash, expected_hash)


def new_session_expiry() -> datetime:
    """Return a UTC timestamp for when a session should expire."""
    return datetime.now(timezone.utc) + SESSION_TTL


def ensure_utc(dt: datetime) -> datetime:
    """Coerce a datetime into a timezone-aware UTC datetime."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)