# database.py
import sqlite3
import os
from datetime import datetime, timezone
from typing import Optional, Dict, Any

# Use data directory for Docker volume, fallback to current dir for local dev
DB_DIR = os.getenv("DB_DIR", ".")
os.makedirs(DB_DIR, exist_ok=True)
DB_NAME = os.path.join(DB_DIR, "database.sqlite")

def get_conn():
    conn = sqlite3.connect(DB_NAME)
    conn.execute("PRAGMA foreign_keys = ON")
    return conn

def init_db():
    conn = get_conn()
    cur = conn.cursor()

    cur.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)

    cur.execute("""
        CREATE TABLE IF NOT EXISTS sessions (
            token TEXT PRIMARY KEY,
            user_id INTEGER NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            expires_at TIMESTAMP NOT NULL,
            last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
    """)

    cur.execute("""
        CREATE TABLE IF NOT EXISTS folders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            folder_name TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(user_id, folder_name),
            FOREIGN KEY (user_id) REFERENCES users(id)
        )
    """)

    cur.execute("""
        CREATE TABLE IF NOT EXISTS images (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER,
            folder_id INTEGER,
            filepath TEXT,
            uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id),
            FOREIGN KEY (folder_id) REFERENCES folders(id)  
        )
    """)

    cur.execute("""
        CREATE TABLE IF NOT EXISTS folder_shares (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            folder_id INTEGER NOT NULL,
            owner_id INTEGER NOT NULL,
            shared_with_user_id INTEGER NOT NULL,
            permission TEXT DEFAULT 'view',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(folder_id, shared_with_user_id),
            FOREIGN KEY (folder_id) REFERENCES folders(id) ON DELETE CASCADE,
            FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (shared_with_user_id) REFERENCES users(id) ON DELETE CASCADE
        )
    """)

    conn.commit()
    conn.close()

def delete_folder_by_id(folder_id, user_id):
    conn = get_conn()
    cur = conn.cursor()
    
    try:
        print(f"[DB] Deleting images for folder_id={folder_id}")
        cur.execute("DELETE FROM images WHERE folder_id = ?", (folder_id,))
        images_deleted = cur.rowcount
        print(f"[DB] Images deleted: {images_deleted}")
        
        print(f"[DB] Deleting folder with id={folder_id} and user_id={user_id}")
        cur.execute("DELETE FROM folders WHERE id = ? AND user_id = ?", (folder_id, user_id))
        folders_deleted = cur.rowcount
        print(f"[DB] Folders deleted: {folders_deleted}")
        
        if folders_deleted == 0:
            raise ValueError(f"Folder {folder_id} not found or you don't have permission to delete it")
        
        conn.commit()
        print(f"[DB] Changes committed to database")
        return True
    except sqlite3.Error as e:
        conn.rollback()
        print(f"[DB] Database error occurred: {e}")
        raise  # Re-raise the exception so the endpoint knows it failed
    except ValueError as e:
        conn.rollback()
        print(f"[DB] Validation error: {e}")
        raise  # Re-raise the exception
    finally:
        conn.close()

def delete_db():
    try:
        conn = get_conn()
        cur = conn.cursor()
        cur.execute("DROP TABLE IF EXISTS users")
        cur.execute("DROP TABLE IF EXISTS folders")
        cur.execute("DROP TABLE IF EXISTS images")
        conn.commit()
        
    except sqlite3.Error as e:
        print(f"An error occurred: {e}")
    finally:
        conn.close()

        
def delete_user_from_db(user_id):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("DELETE FROM folders WHERE user_id = ?", (user_id,))
    cur.execute("DELETE FROM images WHERE user_id = ?", (user_id,))
    cur.execute("DELETE FROM users WHERE id = ?", (user_id,))
    conn.commit()
    conn.close()
    
def add_user(user):
    from security import hash_password
    username = user.username
    password = user.password
    if not username or not password:
        raise ValueError("Username and password cannot be empty")
    conn = get_conn()
    cur = conn.cursor()
    hashed_password = hash_password(password)
    try:
        cur.execute("INSERT INTO users (username, password) VALUES (?, ?)", (username, hashed_password))
        conn.commit()
        user_id = cur.lastrowid
        conn.close()
        return user_id
    except sqlite3.IntegrityError:
        conn.close()
        raise ValueError(f"Username '{username}' is already taken")

def get_user_by_username_and_password(username, password):
    from security import verify_password
    user = get_user_by_username(username)
    if not user:
        return None
    return user if verify_password(password, user[2]) else None

def get_user_by_username(username):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("SELECT * FROM users WHERE username = ?", (username, ))
    row = cur.fetchone()
    conn.close()
    return row

def get_user_by_id(user_id):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("SELECT * FROM users WHERE id = ?", (user_id,))
    row = cur.fetchone()
    conn.close()
    return row


def create_session_record(token: str, user_id: int, expires_at: datetime) -> None:
    conn = get_conn()
    cur = conn.cursor()
    cur.execute(
        "INSERT OR REPLACE INTO sessions (token, user_id, expires_at, last_seen) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
        (token, user_id, expires_at.astimezone(timezone.utc).isoformat()),
    )
    conn.commit()
    conn.close()


def get_session_record(token: str) -> Optional[Dict[str, Any]]:
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("SELECT user_id, expires_at FROM sessions WHERE token = ?", (token,))
    row = cur.fetchone()
    conn.close()
    if not row:
        return None
    return {"user_id": row[0], "expires_at": row[1]}


def refresh_session_expiry(token: str, new_expires_at: datetime) -> None:
    conn = get_conn()
    cur = conn.cursor()
    cur.execute(
        "UPDATE sessions SET expires_at = ?, last_seen = CURRENT_TIMESTAMP WHERE token = ?",
        (new_expires_at.astimezone(timezone.utc).isoformat(), token),
    )
    conn.commit()
    conn.close()


def delete_session_record(token: str) -> None:
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("DELETE FROM sessions WHERE token = ?", (token,))
    conn.commit()
    conn.close()


def delete_sessions_for_user(user_id: int) -> None:
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("DELETE FROM sessions WHERE user_id = ?", (user_id,))
    conn.commit()
    conn.close()


def cleanup_expired_sessions(reference: Optional[datetime] = None) -> None:
    conn = get_conn()
    cur = conn.cursor()
    now = (reference or datetime.now(timezone.utc)).astimezone(timezone.utc).isoformat()
    cur.execute("DELETE FROM sessions WHERE expires_at <= ?", (now,))
    conn.commit()
    conn.close()

    
def add_folder(user_id, folder_name):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("INSERT INTO folders (user_id, folder_name) VALUES (?, ?)", (user_id, folder_name))
    conn.commit()
    folder_id = cur.lastrowid
    conn.close()
    return folder_id
    
def add_image_to_images(user_id, filepath, folder_id):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("INSERT INTO images (user_id, filepath, folder_id) VALUES (?, ?, ?)",
                (user_id, filepath, folder_id))
    conn.commit()
    conn.close()
    return cur.lastrowid

def get_user_images(user_id):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("SELECT filepath FROM images WHERE user_id = ?", (user_id,))
    rows = cur.fetchall()
    conn.close()
    return [r[0] for r in rows]

def get_folders_by_user_id(user_id):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("SELECT id, folder_name FROM folders WHERE user_id = ?", (user_id,))
    rows = cur.fetchall()
    conn.close()
    return [{"id": row[0], "folder_name": row[1]} for row in rows]

def get_folders_by_user_id_and_folder_name(user_id, folder_name):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("SELECT id FROM folders WHERE user_id = ? AND folder_name = ?", (user_id, folder_name))
    row = cur.fetchone()
    conn.close()
    return row[0] if row else None

def get_image_path_by_image_id(image_id):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("SELECT filepath FROM images WHERE id = ?", (int(image_id),))
    row = cur.fetchone()
    conn.close()
    return row[0] if row else None


# ============== Folder Sharing Functions ==============

def share_folder_with_user(folder_id: int, owner_id: int, shared_with_username: str, permission: str = "view"):
    """
    Share a folder with another user by username.
    Returns the share_id if successful, raises exception if user not found or already shared.
    """
    # Get the user_id of the person we're sharing with
    shared_with_user = get_user_by_username(shared_with_username)
    if not shared_with_user:
        raise ValueError(f"User '{shared_with_username}' not found")
    
    shared_with_user_id = shared_with_user[0]
    
    # Don't allow sharing with yourself
    if owner_id == shared_with_user_id:
        raise ValueError("Cannot share folder with yourself")
    
    conn = get_conn()
    cur = conn.cursor()
    try:
        cur.execute("""
            INSERT INTO folder_shares (folder_id, owner_id, shared_with_user_id, permission)
            VALUES (?, ?, ?, ?)
        """, (folder_id, owner_id, shared_with_user_id, permission))
        conn.commit()
        share_id = cur.lastrowid
        return share_id
    except sqlite3.IntegrityError:
        raise ValueError(f"Folder already shared with '{shared_with_username}'")
    finally:
        conn.close()


def revoke_folder_share(folder_id: int, owner_id: int, shared_with_username: str):
    """
    Revoke folder sharing access from a user.
    Returns True if successful, False if share didn't exist.
    """
    shared_with_user = get_user_by_username(shared_with_username)
    if not shared_with_user:
        return False
    
    shared_with_user_id = shared_with_user[0]
    
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("""
        DELETE FROM folder_shares 
        WHERE folder_id = ? AND owner_id = ? AND shared_with_user_id = ?
    """, (folder_id, owner_id, shared_with_user_id))
    conn.commit()
    deleted = cur.rowcount > 0
    conn.close()
    return deleted


def get_folders_shared_with_user(user_id: int):
    """
    Get all folders that have been shared WITH this user.
    Returns list of dicts with folder info and owner info.
    """
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("""
        SELECT 
            f.id, 
            f.folder_name, 
            f.user_id as owner_id,
            u.username as owner_username,
            fs.permission,
            fs.created_at as shared_at
        FROM folder_shares fs
        JOIN folders f ON fs.folder_id = f.id
        JOIN users u ON f.user_id = u.id
        WHERE fs.shared_with_user_id = ?
        ORDER BY fs.created_at DESC
    """, (user_id,))
    rows = cur.fetchall()
    conn.close()
    
    return [{
        "id": row[0],
        "folder_name": row[1],
        "owner_id": row[2],
        "owner_username": row[3],
        "permission": row[4],
        "shared_at": row[5],
        "is_shared": True
    } for row in rows]


def get_folders_shared_by_user(user_id: int):
    """
    Get all folders that this user has shared with others.
    Returns list of dicts with folder info and who it's shared with.
    """
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("""
        SELECT 
            f.id,
            f.folder_name,
            u.username as shared_with_username,
            fs.permission,
            fs.created_at as shared_at
        FROM folder_shares fs
        JOIN folders f ON fs.folder_id = f.id
        JOIN users u ON fs.shared_with_user_id = u.id
        WHERE fs.owner_id = ?
        ORDER BY f.folder_name, fs.created_at DESC
    """, (user_id,))
    rows = cur.fetchall()
    conn.close()
    
    result = {}
    for row in rows:
        folder_id = row[0]
        if folder_id not in result:
            result[folder_id] = {
                "id": folder_id,
                "folder_name": row[1],
                "shared_with": []
            }
        result[folder_id]["shared_with"].append({
            "username": row[2],
            "permission": row[3],
            "shared_at": row[4]
        })
    
    return list(result.values())


def check_folder_access(user_id: int, folder_id: int):
    """
    Check if a user has access to a folder (either owns it or it's shared with them).
    Returns dict with access info or None if no access.
    """
    conn = get_conn()
    cur = conn.cursor()
    
    # Check if user owns the folder
    cur.execute("SELECT id, folder_name, user_id FROM folders WHERE id = ? AND user_id = ?", (folder_id, user_id))
    row = cur.fetchone()
    if row:
        conn.close()
        return {
            "has_access": True,
            "is_owner": True,
            "permission": "owner",
            "folder_id": row[0],
            "folder_name": row[1]
        }
    
    # Check if folder is shared with user
    cur.execute("""
        SELECT fs.permission, f.folder_name, f.user_id
        FROM folder_shares fs
        JOIN folders f ON fs.folder_id = f.id
        WHERE fs.folder_id = ? AND fs.shared_with_user_id = ?
    """, (folder_id, user_id))
    row = cur.fetchone()
    conn.close()
    
    if row:
        return {
            "has_access": True,
            "is_owner": False,
            "permission": row[0],
            "folder_id": folder_id,
            "folder_name": row[1],
            "owner_id": row[2]
        }
    
    return None


def get_all_accessible_folders(user_id: int):
    """
    Get all folders accessible to a user (owned + shared).
    Returns combined list with ownership info.
    """
    # Get owned folders
    owned = get_folders_by_user_id(user_id)
    for folder in owned:
        folder["is_owner"] = True
        folder["is_shared"] = False
    
    # Get shared folders
    shared = get_folders_shared_with_user(user_id)
    for folder in shared:
        folder["is_owner"] = False
    
    return owned + shared

