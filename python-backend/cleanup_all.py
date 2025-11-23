#!/usr/bin/env python3
"""
Cleanup Script for Image Search App
====================================
This script deletes ALL data for debugging purposes:
- PostgreSQL database records (all tables)
- All uploaded images
- All FAISS indexes

WARNING: This action is IRREVERSIBLE!
Use only for development/debugging.
"""

import os
import shutil
import sys
from pathlib import Path
import psycopg2
from dotenv import load_dotenv

# Colors for terminal output
RED = '\033[91m'
GREEN = '\033[92m'
YELLOW = '\033[93m'
BLUE = '\033[94m'
RESET = '\033[0m'

def print_color(message, color):
    print(f"{color}{message}{RESET}")

def confirm_deletion():
    """Ask user to confirm before deleting everything."""
    print_color("\n⚠️  WARNING: This will DELETE ALL DATA!", RED)
    print_color("This includes:", YELLOW)
    print("  • All user accounts (PostgreSQL)")
    print("  • All folders and images (PostgreSQL)")
    print("  • All sessions (PostgreSQL)")
    print("  • All uploaded image files")
    print("  • All FAISS indexes")
    print("\nNote: Database tables will be preserved (only data is deleted)")

    response = input(f"\n{RED}Are you sure? Type 'YES' to confirm: {RESET}")
    return response == "YES"

def delete_database():
    """Clear all data from PostgreSQL database (keeps tables)."""
    try:
        # Load environment variables
        load_dotenv()

        # Get database credentials
        db_host = os.getenv("DB_HOST", "localhost")
        db_port = os.getenv("DB_PORT", "5432")
        db_name = os.getenv("DB_NAME", "imagesearch")
        db_user = os.getenv("DB_USERNAME", "imageuser")
        db_pass = os.getenv("DB_PASSWORD", "imagepass123")

        print_color(f"   Connecting to PostgreSQL database: {db_name}@{db_host}:{db_port}", BLUE)

        # Connect to PostgreSQL
        conn = psycopg2.connect(
            host=db_host,
            port=db_port,
            database=db_name,
            user=db_user,
            password=db_pass
        )
        cursor = conn.cursor()

        # Get record counts before deletion
        cursor.execute("SELECT COUNT(*) FROM users")
        user_count = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM folders")
        folder_count = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM images")
        image_count = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM sessions")
        session_count = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM folder_shares")
        share_count = cursor.fetchone()[0]

        total_records = user_count + folder_count + image_count + session_count + share_count

        if total_records == 0:
            print_color(f"   Database is already empty", YELLOW)
            conn.close()
            return False

        print_color(f"   Found {total_records} records:", YELLOW)
        print(f"      • {user_count} users")
        print(f"      • {folder_count} folders")
        print(f"      • {image_count} images")
        print(f"      • {session_count} sessions")
        print(f"      • {share_count} folder shares")

        # Delete all data (CASCADE will handle foreign keys)
        print_color(f"\n   Deleting all records...", BLUE)
        cursor.execute("TRUNCATE folder_shares, images, sessions, folders, users CASCADE")
        conn.commit()

        print_color(f"   ✅ Deleted all {total_records} records from PostgreSQL database", GREEN)
        print_color(f"   ✅ Tables preserved (ready for new data)", GREEN)

        conn.close()
        return True

    except psycopg2.Error as e:
        print_color(f"   ❌ Database error: {e}", RED)
        print_color(f"   Make sure PostgreSQL is running and credentials are correct", YELLOW)
        return False
    except Exception as e:
        print_color(f"   ❌ Error clearing database: {e}", RED)
        return False


def delete_images():
    """Delete all uploaded images."""
    # Look for images in both backend/ and project root
    images_locations = [
        "images",  # backend/images
        "../images"  # project-root/images
    ]

    deleted = False
    for images_dir in images_locations:
        if os.path.exists(images_dir):
            # Count files before deletion
            file_count = sum(len(files) for _, _, files in os.walk(images_dir))
            shutil.rmtree(images_dir)
            print_color(f"   ✅ Deleted {file_count} images from: {images_dir}", GREEN)
            deleted = True

    if not deleted:
        print_color(f"   ⚠️  Images directory not found", YELLOW)
    return deleted


def delete_faiss_indexes():
    """Delete all FAISS indexes."""
    faiss_dir = "faisses_indexes"
    if os.path.exists(faiss_dir):
        # Count index files before deletion
        index_count = sum(1 for _, _, files in os.walk(faiss_dir)
                         for f in files if f.endswith('.faiss'))
        shutil.rmtree(faiss_dir)
        print_color(f"   ✅ Deleted {index_count} FAISS indexes from: {faiss_dir}", GREEN)
        return True
    else:
        print_color(f"   ⚠️  FAISS directory not found: {faiss_dir}", YELLOW)
        return False


def recreate_directories():
    """Recreate empty directories for fresh start."""
    # Recreate in project root
    os.makedirs("../images", exist_ok=True)
    os.makedirs("faisses_indexes", exist_ok=True)
    print_color("   ✅ Recreated empty directories", GREEN)

def main():
    """Main cleanup function."""
    print_color("\n" + "="*50, BLUE)
    print_color("  Image Search App - Cleanup Script", BLUE)
    print_color("="*50 + "\n", BLUE)

    # Check if we're in the backend directory
    if not os.path.exists("api.py"):
        print_color("   ❌ Error: Please run this script from the backend/ directory", RED)
        print_color("   cd backend/", YELLOW)
        print_color("   python cleanup_all.py", YELLOW)
        sys.exit(1)

    # Confirm deletion
    if not confirm_deletion():
        print_color("\n   Cleanup cancelled.", YELLOW)
        sys.exit(0)

    print_color("\n   Starting cleanup...\n", BLUE)

    # Perform deletions
    deleted_count = 0

    if delete_database():
        deleted_count += 1

    if delete_images():
        deleted_count += 1

    if delete_faiss_indexes():
        deleted_count += 1

    # Recreate empty directories
    print()
    recreate_directories()

    # Summary
    print_color("\n" + "="*50, BLUE)
    if deleted_count > 0:
        print_color(f"   ✅ Cleanup complete! Removed {deleted_count} item(s)", GREEN)
        print_color("\n   You can now:", BLUE)
        print("     • Restart the backend server (Python or Java)")
        print("     • Register a new user")
        print("     • Start fresh with clean data")
        print_color("\n   PostgreSQL database tables are ready to use!", GREEN)
    else:
        print_color("   Nothing to clean up - everything was already empty", YELLOW)
    print_color("="*50 + "\n", BLUE)

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print_color("\n\nCleanup interrupted by user", YELLOW)
        sys.exit(1)
    except Exception as e:
        print_color(f"\nError during cleanup: {e}", RED)
        sys.exit(1)
