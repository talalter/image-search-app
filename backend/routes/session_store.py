sessions = {}

def get_user_id_from_token(token: str) -> int:
    if token not in sessions:
        raise Exception("Invalid or expired token")
    return sessions[token]