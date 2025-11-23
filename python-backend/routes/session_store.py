from __future__ import annotations

import secrets
from datetime import datetime, timezone

from database import (
    init_db,
    cleanup_expired_sessions,
    create_session_record,
    delete_session_record,
    delete_sessions_for_user,
    get_session_record,
    refresh_session_expiry,
)
from security import new_session_expiry, ensure_utc

def _now() -> datetime:
    return datetime.now(timezone.utc)

init_db()

def issue_session_token(user_id: int) -> str:
    """Create a new session token for a user and persist it."""
    cleanup_expired_sessions()
    token = secrets.token_urlsafe(32)
    expires_at = new_session_expiry()
    create_session_record(token, user_id, expires_at)
    return token


def get_user_id_from_token(token: str) -> int:
    cleanup_expired_sessions()
    session = get_session_record(token)
    if not session:
        raise Exception("Invalid or expired token")

    # Handle expires_at - could be datetime object or string depending on database driver
    expires_at = session["expires_at"]
    if isinstance(expires_at, str):
        expires_at = ensure_utc(datetime.fromisoformat(expires_at))
    else:
        # Already a datetime object from PostgreSQL
        expires_at = ensure_utc(expires_at)

    if expires_at <= _now():
        delete_session_record(token)
        raise Exception("Invalid or expired token")

    refresh_session_expiry(token, new_session_expiry())
    return session["user_id"]

def invalidate_session(token: str) -> None:
    delete_session_record(token)


def invalidate_user_sessions(user_id: int) -> None:
    delete_sessions_for_user(user_id)