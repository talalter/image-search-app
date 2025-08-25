# database.py
import sqlite3

DB_NAME = "database.sqlite"

def get_conn():
    return sqlite3.connect(DB_NAME)

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

    conn.commit()
    conn.close()

def delete_folder_by_id(folder_id, user_id):
    try:
        conn = get_conn()
        cur = conn.cursor()
        cur.execute("DELETE FROM folders WHERE id = ? AND user_id = ?", (folder_id, user_id))
        cur.execute("DELETE FROM images WHERE folder_id = ?", (folder_id,))
        conn.commit()
    except sqlite3.Error as e:
        print(f"An error occurred: {e}")
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
    username = user.username
    password = user.password
    if not username or not password:
        raise ValueError("Username and password cannot be empty")
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("INSERT INTO users (username, password) VALUES (?, ?)", (username, password))
    conn.commit()
    user_id = cur.lastrowid
    conn.close()
    return user_id

def get_user_by_username_and_password(username, password):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("SELECT * FROM users WHERE username = ? AND password = ?", (username, password))
    row = cur.fetchone()
    conn.close()
    return row

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

