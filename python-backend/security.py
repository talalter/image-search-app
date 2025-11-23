"""Security utilities for password hashing and verification using BCrypt."""
from __future__ import annotations

import bcrypt
from datetime import datetime, timedelta, timezone


SESSION_TTL = timedelta(hours=12)


def hash_password(password: str) -> str:
    """
    Hash a password using BCrypt.

    BCrypt is the industry standard for password hashing:
    - Adaptive cost factor (future-proof against hardware advances)
    - Built-in salt generation
    - Compatible with Java Spring Security
    - Resistant to rainbow table attacks

    Args:
        password: Plain text password to hash

    Returns:
        BCrypt hash string (format: $2b$12$...)

    Raises:
        ValueError: If password is empty
    """
    if not password:
        raise ValueError("Password cannot be empty")

    # Generate salt and hash password
    # BCrypt automatically handles salt generation and embedding
    salt = bcrypt.gensalt(rounds=12)  # Cost factor of 12 (recommended)
    hashed = bcrypt.hashpw(password.encode('utf-8'), salt)

    return hashed.decode('utf-8')


def verify_password(password: str, stored_hash: str) -> bool:
    """
    Verify a candidate password against a stored BCrypt hash.

    Uses constant-time comparison to prevent timing attacks.

    Args:
        password: Plain text password to verify
        stored_hash: BCrypt hash from database

    Returns:
        True if password matches, False otherwise
    """
    try:
        return bcrypt.checkpw(password.encode('utf-8'), stored_hash.encode('utf-8'))
    except Exception:
        # Invalid hash format or other error
        return False


def new_session_expiry() -> datetime:
    """Return a UTC timestamp for when a session should expire."""
    return datetime.now(timezone.utc) + SESSION_TTL


def ensure_utc(dt: datetime) -> datetime:
    """Coerce a datetime into a timezone-aware UTC datetime."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)
