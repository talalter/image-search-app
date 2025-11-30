# Deployment Options

### Option 1: Python Search Service Only (Default)

Start the Java backend stack with Python search service only:

```bash
# Start the main stack (backend + Python search service + frontend)
docker-compose -f docker-compose.java.yml up -d

# The Java backend will use Python search service on port 5000
# No Elasticsearch required
```

### Option 2: Java Search Service with Elasticsearch (Optional)

If you want to use Java search service as primary with circuit breaker failover:

```bash
# Step 1: Start main stack with Python search service
docker-compose -f docker-compose.java.yml up -d

# Step 2: Start Java search service with Elasticsearch
./scripts/start-java-search.sh
# OR manually:
docker-compose -f docker-compose.java-search.yml up -d

# Step 3: Update Java backend to use Java search service as primary
docker-compose -f docker-compose.java.yml exec java-backend \
  sh -c 'export SEARCH_BACKEND=java && java -jar app.jar'
```

**Note:** By default, the Java backend uses `SEARCH_BACKEND=python`. To enable circuit breaker failover between both services, you need to:

1. Start both search services
2. Set `SEARCH_BACKEND=java` in the Java backend container

### Stopping Services

```bash
# Stop main stack
docker-compose -f docker-compose.java.yml down

# Stop Java search service + Elasticsearch (if running)
./scripts/stop-java-search.sh
# OR manually:
docker-compose -f docker-compose.java-search.yml down
```