#!/bin/bash
# PostgreSQL Database Manager for Image Search App
# Easy commands to view and manage your database

DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="imagesearch"
DB_USER="imageuser"
DB_PASS="imagepass123"

# Helper function to run psql commands
run_sql() {
    PGPASSWORD=$DB_PASS psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c "$1"
}

# Display menu
show_menu() {
    echo "================================================"
    echo "  PostgreSQL Database Manager"
    echo "  Database: $DB_NAME"
    echo "================================================"
    echo ""
    echo "1. View all tables"
    echo "2. Count all records"
    echo "3. View all users"
    echo "4. View all folders"
    echo "5. View all images"
    echo "6. View all sessions"
    echo "7. View folder shares"
    echo "8. Delete all data (keep tables)"
    echo "9. Drop all tables (destructive!)"
    echo "10. Open interactive psql shell"
    echo "0. Exit"
    echo ""
}

case "$1" in
    1|tables)
        echo "📋 All tables in database:"
        run_sql "\dt"
        ;;
    2|count)
        echo "📊 Record counts:"
        run_sql "
            SELECT 'Users' as table_name, COUNT(*) as count FROM users
            UNION ALL
            SELECT 'Folders', COUNT(*) FROM folders
            UNION ALL
            SELECT 'Images', COUNT(*) FROM images
            UNION ALL
            SELECT 'Sessions', COUNT(*) FROM sessions
            UNION ALL
            SELECT 'Folder Shares', COUNT(*) FROM folder_shares;
        "
        ;;
    3|users)
        echo "👥 All users:"
        run_sql "SELECT id, username, created_at FROM users ORDER BY created_at DESC;"
        ;;
    4|folders)
        echo "📁 All folders:"
        run_sql "SELECT f.id, f.folder_name, u.username as owner, f.created_at
                 FROM folders f
                 JOIN users u ON f.user_id = u.id
                 ORDER BY f.created_at DESC;"
        ;;
    5|images)
        echo "🖼️  All images:"
        run_sql "SELECT i.id, i.filepath, u.username as owner, f.folder_name, i.uploaded_at
                 FROM images i
                 LEFT JOIN users u ON i.user_id = u.id
                 LEFT JOIN folders f ON i.folder_id = f.id
                 ORDER BY i.uploaded_at DESC LIMIT 20;"
        ;;
    6|sessions)
        echo "🔐 Active sessions:"
        run_sql "SELECT s.token, u.username, s.created_at, s.expires_at, s.last_seen
                 FROM sessions s
                 JOIN users u ON s.user_id = u.id
                 WHERE s.expires_at > NOW()
                 ORDER BY s.last_seen DESC;"
        ;;
    7|shares)
        echo "🤝 Folder shares:"
        run_sql "SELECT fs.id, f.folder_name,
                        u1.username as owner,
                        u2.username as shared_with,
                        fs.permission,
                        fs.created_at
                 FROM folder_shares fs
                 JOIN folders f ON fs.folder_id = f.id
                 JOIN users u1 ON fs.owner_id = u1.id
                 JOIN users u2 ON fs.shared_with_user_id = u2.id
                 ORDER BY fs.created_at DESC;"
        ;;
    8|clear)
        echo "⚠️  WARNING: This will delete ALL data but keep tables!"
        read -p "Are you sure? Type 'yes' to confirm: " confirm
        if [ "$confirm" = "yes" ]; then
            echo "Deleting all data..."
            run_sql "TRUNCATE folder_shares, images, sessions, folders, users CASCADE;"
            echo "✅ All data deleted (tables preserved)"
        else
            echo "Cancelled"
        fi
        ;;
    9|drop)
        echo "🚨 DANGER: This will drop ALL tables!"
        read -p "Are you ABSOLUTELY sure? Type 'DELETE EVERYTHING' to confirm: " confirm
        if [ "$confirm" = "DELETE EVERYTHING" ]; then
            echo "Dropping all tables..."
            run_sql "DROP TABLE IF EXISTS folder_shares, images, sessions, folders, users CASCADE;"
            echo "✅ All tables dropped"
        else
            echo "Cancelled"
        fi
        ;;
    10|shell)
        echo "🐚 Opening psql shell..."
        echo "Use \q to quit"
        PGPASSWORD=$DB_PASS psql -h $DB_HOST -U $DB_USER -d $DB_NAME
        ;;
    0|exit)
        echo "Goodbye!"
        exit 0
        ;;
    *)
        # Interactive menu mode
        while true; do
            show_menu
            read -p "Choose an option: " choice
            echo ""
            case $choice in
                1) $0 tables ;;
                2) $0 count ;;
                3) $0 users ;;
                4) $0 folders ;;
                5) $0 images ;;
                6) $0 sessions ;;
                7) $0 shares ;;
                8) $0 clear ;;
                9) $0 drop ;;
                10) $0 shell ;;
                0) exit 0 ;;
                *) echo "Invalid option!" ;;
            esac
            echo ""
            read -p "Press Enter to continue..."
        done
        ;;
esac
