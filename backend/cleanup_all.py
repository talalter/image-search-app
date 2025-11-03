#!/usr/bin/env python3
"""
Cleanup Script for Image Search App
====================================
This script deletes ALL data for debugging purposes:
- SQLite database
- All uploaded images
- All FAISS indexes

WARNING: This action is IRREVERSIBLE!
Use only for development/debugging.
"""

import os
import shutil
import sys
from pathlib import Path

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
    print("  • All user accounts")
    print("  • All uploaded images")
    print("  • All FAISS indexes")
    print("  • The entire database")
    
    response = input(f"\n{RED}Are you sure? Type 'YES' to confirm: {RESET}")
    return response == "YES"

def delete_database():
    """Delete the SQLite database file."""
    db_file = "database.sqlite"
    if os.path.exists(db_file):
        os.remove(db_file)
        print_color(f"✓ Deleted database: {db_file}", GREEN)
        return True
    else:
        print_color(f"⊘ Database not found: {db_file}", YELLOW)
        return False

def delete_images():
    """Delete all uploaded images."""
    images_dir = "images"
    if os.path.exists(images_dir):
        shutil.rmtree(images_dir)
        print_color(f"✓ Deleted images directory: {images_dir}", GREEN)
        return True
    else:
        print_color(f"⊘ Images directory not found: {images_dir}", YELLOW)
        return False

def delete_faiss_indexes():
    """Delete all FAISS indexes."""
    faiss_dir = "faisses_indexes"
    if os.path.exists(faiss_dir):
        shutil.rmtree(faiss_dir)
        print_color(f"✓ Deleted FAISS indexes: {faiss_dir}", GREEN)
        return True
    else:
        print_color(f"⊘ FAISS directory not found: {faiss_dir}", YELLOW)
        return False

def recreate_directories():
    """Recreate empty directories for fresh start."""
    os.makedirs("images", exist_ok=True)
    os.makedirs("faisses_indexes", exist_ok=True)
    print_color("✓ Recreated empty directories", GREEN)

def main():
    """Main cleanup function."""
    print_color("\n" + "="*50, BLUE)
    print_color("  Image Search App - Cleanup Script", BLUE)
    print_color("="*50 + "\n", BLUE)
    
    # Check if we're in the backend directory
    if not os.path.exists("api.py"):
        print_color("❌ Error: Please run this script from the backend/ directory", RED)
        print_color("   cd backend/", YELLOW)
        print_color("   python cleanup_all.py", YELLOW)
        sys.exit(1)
    
    # Confirm deletion
    if not confirm_deletion():
        print_color("\n✖ Cleanup cancelled.", YELLOW)
        sys.exit(0)
    
    print_color("\n🗑️  Starting cleanup...\n", BLUE)
    
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
        print_color(f"✓ Cleanup complete! Removed {deleted_count} item(s)", GREEN)
        print_color("\nYou can now:", BLUE)
        print("  • Restart the backend server")
        print("  • Register a new user")
        print("  • Start fresh with clean data")
    else:
        print_color("⊘ Nothing to clean up - all directories were already empty", YELLOW)
    print_color("="*50 + "\n", BLUE)

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print_color("\n\n✖ Cleanup interrupted by user", YELLOW)
        sys.exit(1)
    except Exception as e:
        print_color(f"\n❌ Error during cleanup: {e}", RED)
        sys.exit(1)
