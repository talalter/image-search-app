# PostgreSQL Setup Guide

This guide explains how to set up PostgreSQL for the Image Search application.

## Why PostgreSQL?

The Java backend uses PostgreSQL instead of SQLite because:
- **Better concurrency**: PostgreSQL handles multiple simultaneous connections without locking
- **Production-ready**: Industry-standard RDBMS used by major companies
- **ACID compliance**: Full transaction support with rollback capabilities
- **Spring Boot integration**: Excellent support via Spring Data JPA

## Installation

### Ubuntu/Debian:
```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
```

### macOS (using Homebrew):
```bash
brew install postgresql@15
brew services start postgresql@15
```

### Windows:
Download and install from: https://www.postgresql.org/download/windows/

## Setup Steps

### 1. Start PostgreSQL service
```bash
# Ubuntu/Debian
sudo systemctl start postgresql
sudo systemctl enable postgresql

# macOS (Homebrew)
brew services start postgresql@15
```

### 2. Create database and user
```bash
# Switch to postgres user
sudo -u postgres psql

# In PostgreSQL prompt:
CREATE DATABASE imagesearch;
CREATE USER imageuser WITH ENCRYPTED PASSWORD 'your_password_here';
GRANT ALL PRIVILEGES ON DATABASE imagesearch TO imageuser;

# Exit
\q
```

### 3. Configure Java backend

Update `java-backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/imagesearch
    username: imageuser
    password: your_password_here
```

Or use environment variables:
```bash
export DB_USERNAME=imageuser
export DB_PASSWORD=your_password_here
```

### 4. Verify connection

The Java backend will automatically create tables on first run using Hibernate DDL.

Check the logs when starting the application:
```
Hibernate: create table users (...)
Hibernate: create table folders (...)
Hibernate: create table images (...)
Hibernate: create table sessions (...)
Hibernate: create table folder_shares (...)
```

## Database Schema

The application creates the following tables:

### `users`
- `id` (BIGSERIAL PRIMARY KEY)
- `username` (VARCHAR UNIQUE NOT NULL)
- `password` (VARCHAR NOT NULL) - BCrypt hashed
- `created_at` (TIMESTAMP)

### `sessions`
- `token` (VARCHAR PRIMARY KEY)
- `user_id` (BIGINT FOREIGN KEY → users.id)
- `created_at` (TIMESTAMP)
- `expires_at` (TIMESTAMP)
- `last_seen` (TIMESTAMP)

### `folders`
- `id` (BIGSERIAL PRIMARY KEY)
- `user_id` (BIGINT FOREIGN KEY → users.id)
- `folder_name` (VARCHAR)
- `created_at` (TIMESTAMP)
- UNIQUE constraint on (user_id, folder_name)

### `images`
- `id` (BIGSERIAL PRIMARY KEY)
- `user_id` (BIGINT FOREIGN KEY → users.id)
- `folder_id` (BIGINT FOREIGN KEY → folders.id)
- `filepath` (VARCHAR)
- `uploaded_at` (TIMESTAMP)

### `folder_shares`
- `id` (BIGSERIAL PRIMARY KEY)
- `folder_id` (BIGINT FOREIGN KEY → folders.id)
- `owner_id` (BIGINT FOREIGN KEY → users.id)
- `shared_with_user_id` (BIGINT FOREIGN KEY → users.id)
- `permission` (VARCHAR DEFAULT 'view')
- `created_at` (TIMESTAMP)
- UNIQUE constraint on (folder_id, shared_with_user_id)

## Useful PostgreSQL Commands

```bash
# Connect to database
psql -U imageuser -d imagesearch

# List tables
\dt

# Describe table structure
\d users
\d folders
\d images

# View all users
SELECT * FROM users;

# View all folders
SELECT * FROM folders;

# Exit
\q
```

## Troubleshooting

### Can't connect to PostgreSQL
```bash
# Check if PostgreSQL is running
sudo systemctl status postgresql

# Check PostgreSQL logs
sudo tail -f /var/log/postgresql/postgresql-*.log
```

### Authentication failed
Make sure the password in `application.yml` matches the one you created.

### Tables not created
Check that `spring.jpa.hibernate.ddl-auto` is set to `update` in `application.yml`.

## Production Deployment

For production, consider:
1. Using connection pooling (HikariCP - included by default)
2. Setting `ddl-auto` to `validate` instead of `update`
3. Using database migration tools like Flyway or Liquibase
4. Enabling SSL connections
5. Regular backups with `pg_dump`
