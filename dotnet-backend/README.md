# .NET Backend for Image Search Application

This is a .NET 8 Web API backend that implements the same REST API as the Java Spring Boot and Python FastAPI backends.

## Features

- **Port**: 7000 (coexists with Java:8080, Python:8000)
- **Database**: Shared PostgreSQL database with Java/Python backends
- **Search Service**: Calls Python search microservice (port 5000) for CLIP embeddings and FAISS search
- **JSON Format**: Snake_case serialization (matches frontend expectations)
- **Password Hashing**: BCrypt.Net (compatible with Java BCrypt)
- **Background Processing**: Channel-based queue for embedding generation
- **Session Management**: 12-hour sliding window expiration

## Prerequisites

1. **.NET 8 SDK** - Download from [https://dotnet.microsoft.com/download/dotnet/8.0](https://dotnet.microsoft.com/download/dotnet/8.0)
2. **PostgreSQL** - Running on localhost:5432 with database `imagesearch`
3. **Python Search Service** - Running on localhost:5000

## Quick Start

### 1. Install .NET 8 SDK

**Ubuntu/Debian:**
```bash
wget https://packages.microsoft.com/config/ubuntu/24.04/packages-microsoft-prod.deb -O packages-microsoft-prod.deb
sudo dpkg -i packages-microsoft-prod.deb
sudo apt-get update
sudo apt-get install -y dotnet-sdk-8.0
```

**Other platforms:**
Visit [https://dotnet.microsoft.com/download/dotnet/8.0](https://dotnet.microsoft.com/download/dotnet/8.0)

### 2. Restore NuGet Packages

```bash
cd dotnet-backend/ImageSearch.Api
dotnet restore
```

### 3. Run the Backend

```bash
cd dotnet-backend/ImageSearch.Api
dotnet run
```

The backend will start on **http://localhost:7000**

## Configuration

Configuration is in `appsettings.json`:

```json
{
  "ConnectionStrings": {
    "DefaultConnection": "Host=localhost;Port=5432;Database=imagesearch;Username=imageuser;Password=imagepass123"
  },
  "SearchService": {
    "BaseUrl": "http://localhost:5000"
  }
}
```

### Environment Variables

You can override configuration with environment variables:

```bash
# Database
export ConnectionStrings__DefaultConnection="Host=localhost;Port=5432;Database=imagesearch;Username=imageuser;Password=imagepass123"

# Search Service
export SearchService__BaseUrl="http://localhost:5000"

# Run
dotnet run
```

## API Endpoints

All endpoints match the Java/Python implementations:

### User Management
- `POST /api/users/register` - Register new user
- `POST /api/users/login` - Login and create session
- `POST /api/users/logout` - Invalidate session
- `DELETE /api/users/delete` - Delete account

### Image Operations
- `POST /api/images/upload` - Upload images (multipart/form-data)
- `GET /api/images/search` - Search images by text query

### Folder Management
- `GET /api/folders?token=xxx` - Get accessible folders
- `DELETE /api/folders` - Delete folders
- `POST /api/folders/share` - Share folder with user

### Health
- `GET /health` - Service health check

## Testing

### 1. Health Check

```bash
curl http://localhost:7000/health
```

Expected response:
```json
{
  "status": "Healthy",
  "service": "image-search-dotnet-backend",
  "timestamp": "2026-01-12T14:30:00Z"
}
```

### 2. Register User

```bash
curl -X POST http://localhost:7000/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"dotnetuser","password":"test123"}'
```

Expected response (201 Created):
```json
{
  "id": 1,
  "username": "dotnetuser"
}
```

### 3. Login

```bash
curl -X POST http://localhost:7000/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"dotnetuser","password":"test123"}'
```

Expected response:
```json
{
  "token": "ABC123...",
  "user_id": 1,
  "username": "dotnetuser",
  "message": "Login successful"
}
```

### 4. Upload Images

```bash
TOKEN="<your_token_here>"
curl -X POST http://localhost:7000/api/images/upload \
  -F "token=$TOKEN" \
  -F "folderName=test-folder" \
  -F "files=@/path/to/image.jpg"
```

### 5. Search Images

```bash
TOKEN="<your_token_here>"
curl "http://localhost:7000/api/images/search?token=$TOKEN&query=sunset&top_k=5"
```

## Frontend Integration

To use the .NET backend with the React frontend:

```bash
cd frontend

# Update apiConfig.js or use environment variable
REACT_APP_BACKEND=dotnet npm start

# Or create .env file:
echo "REACT_APP_BACKEND=dotnet" > .env
npm start
```

The frontend will automatically connect to http://localhost:7000

## Development

### Build

```bash
dotnet build
```

### Run with Watch (auto-reload on code changes)

```bash
dotnet watch run
```

### View Logs

Logs are written to the console. For structured logging, configure in `appsettings.json`:

```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning",
      "Microsoft.EntityFrameworkCore": "Information"
    }
  }
}
```

## Project Structure

```
dotnet-backend/ImageSearch.Api/
├── Program.cs                      # Application entry point
├── appsettings.json               # Configuration
├── Controllers/                    # REST API endpoints
├── Services/                       # Business logic
├── Clients/                        # Python search service client
├── Data/                           # EF Core DbContext
├── Models/                         # Entities and DTOs
├── Middleware/                     # Global exception handling
├── Exceptions/                     # Custom exceptions
├── Utilities/                      # Helper classes
└── BackgroundServices/             # Background tasks
```

## Troubleshooting

### Port Already in Use

If port 7000 is already in use:

```bash
# Check what's using port 7000
lsof -i :7000

# Kill the process
kill -9 <PID>

# Or change the port in appsettings.json
```

### Database Connection Errors

Ensure PostgreSQL is running and credentials are correct:

```bash
# Check PostgreSQL status
sudo systemctl status postgresql

# Test connection
psql -h localhost -U imageuser -d imagesearch -c "SELECT 1"
```

### Python Search Service Not Available

The .NET backend requires the Python search service on port 5000:

```bash
cd python-search-service
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python app.py
```

## Performance Notes

- **Async/Await**: All I/O operations are async for optimal performance
- **Background Processing**: Image embeddings processed in background queue
- **Connection Pooling**: EF Core automatically pools database connections
- **Batch Processing**: Embeddings processed in batches of 32

## Comparison with Other Backends

| Feature | .NET | Java Spring Boot | Python FastAPI |
|---------|------|------------------|----------------|
| Port | 7000 | 8080 | 8000 |
| ORM | Entity Framework Core | JPA/Hibernate | Raw psycopg2 |
| Password Hashing | BCrypt.Net | BCrypt | BCrypt |
| JSON Serialization | Newtonsoft.Json | Jackson | Pydantic |
| Background Tasks | Channel<T> + IHostedService | @Async + ExecutorService | BackgroundTasks |
| HTTP Client | HttpClient + Polly | WebClient + Resilience4j | requests/httpx |

All three backends share the same:
- PostgreSQL database
- Python search service (port 5000)
- REST API contract
- JSON snake_case format
- 12-hour session tokens

## License

Same as the main image-search-app project.
