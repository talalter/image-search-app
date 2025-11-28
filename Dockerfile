# Unified Java Stack Dockerfile for Image Search App
# This Dockerfile builds and runs the complete Java stack:
# - PostgreSQL Database  
# - Java Backend (Spring Boot)
# - Java Search Service (ONNX + Elasticsearch)
# - React Frontend (served by Nginx)
# - All required dependencies and configuration

FROM ubuntu:24.04

# Set environment variables
ENV DEBIAN_FRONTEND=noninteractive
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

# Install system dependencies
RUN apt-get update && apt-get install -y \
    # Java 17
    openjdk-17-jdk \
    # PostgreSQL
    postgresql-16 \
    postgresql-client-16 \
    postgresql-contrib-16 \
    # Node.js and npm
    curl \
    # Nginx (for serving React)
    nginx \
    # Elasticsearch GPG and repos
    apt-transport-https \
    ca-certificates \
    gnupg \
    lsb-release \
    software-properties-common \
    # General utilities
    wget \
    unzip \
    supervisor \
    net-tools \
    procps \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js 18
RUN curl -fsSL https://deb.nodesource.com/setup_18.x | bash - \
    && apt-get install -y nodejs

# Install Elasticsearch 8.x
RUN wget -qO - https://artifacts.elastic.co/GPG-KEY-elasticsearch | gpg --dearmor -o /usr/share/keyrings/elasticsearch-keyring.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/elasticsearch-keyring.gpg] https://artifacts.elastic.co/packages/8.x/apt stable main" | tee /etc/apt/sources.list.d/elastic-8.x.list \
    && apt-get update \
    && apt-get install -y elasticsearch \
    && rm -rf /var/lib/apt/lists/*

# Create application user and directories
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Create application directory and set up structure
WORKDIR /app

# Copy project files first (before building)
COPY . /app/

# Create necessary directories with proper permissions
RUN mkdir -p \
    /app/data/uploads \
    /app/data/indexes \
    /app/data/lucene-indexes \
    /app/models \
    /app/logs \
    /var/lib/postgresql/16/main \
    /var/log/postgresql \
    /var/log/elasticsearch \
    /var/lib/elasticsearch \
    && chown -R appuser:appuser /app \
    && chown -R postgres:postgres /var/lib/postgresql /var/log/postgresql \
    && chown -R elasticsearch:elasticsearch /usr/share/elasticsearch /etc/elasticsearch /var/lib/elasticsearch /var/log/elasticsearch

# Build Java Backend
WORKDIR /app/java-backend
RUN chmod +x gradlew \
    && ./gradlew bootJar --no-daemon \
    && cp build/libs/*.jar /app/java-backend.jar

# Build Java Search Service and download models
WORKDIR /app/java-search-service  
RUN chmod +x gradlew download-models.sh \
    && ./gradlew bootJar --no-daemon \
    && cp build/libs/*.jar /app/java-search-service.jar \
    && ./download-models.sh \
    && cp -r models/* /app/models/ 2>/dev/null || echo "No models directory to copy"

# Build React Frontend
WORKDIR /app/frontend
RUN npm install && npm run build

# Configure Nginx for React frontend
RUN rm -rf /var/www/html/* \
    && cp -r build/* /var/www/html/

# Copy nginx config (we'll create it in a separate step)
COPY frontend/nginx.conf /etc/nginx/sites-available/default

# Update nginx config to point to localhost instead of backend container
RUN sed -i 's/backend:8000/localhost:8080/g' /etc/nginx/sites-available/default

# Configure Elasticsearch with minimal memory settings (like optimized script)
RUN echo "cluster.name: imagesearch-cluster" >> /etc/elasticsearch/elasticsearch.yml \
    && echo "node.name: imagesearch-node" >> /etc/elasticsearch/elasticsearch.yml \
    && echo "network.host: 0.0.0.0" >> /etc/elasticsearch/elasticsearch.yml \
    && echo "http.port: 9200" >> /etc/elasticsearch/elasticsearch.yml \
    && echo "discovery.type: single-node" >> /etc/elasticsearch/elasticsearch.yml \
    && echo "xpack.security.enabled: false" >> /etc/elasticsearch/elasticsearch.yml \
    && echo "bootstrap.memory_lock: false" >> /etc/elasticsearch/elasticsearch.yml \
    && echo "indices.fielddata.cache.size: 25%" >> /etc/elasticsearch/elasticsearch.yml \
    && echo "indices.breaker.total.use_real_memory: false" >> /etc/elasticsearch/elasticsearch.yml

# Create Elasticsearch JVM heap optimization (matching optimized script)
RUN mkdir -p /etc/elasticsearch/jvm.options.d && \
    echo "# Optimize for systems with limited RAM" > /etc/elasticsearch/jvm.options.d/heap.options && \
    echo "-Xms512m" >> /etc/elasticsearch/jvm.options.d/heap.options && \
    echo "-Xmx1g" >> /etc/elasticsearch/jvm.options.d/heap.options

# Configure PostgreSQL  
RUN echo "listen_addresses = '*'" >> /etc/postgresql/16/main/postgresql.conf \
    && echo "port = 5432" >> /etc/postgresql/16/main/postgresql.conf

# Create supervisor configuration files
RUN mkdir -p /var/log/supervisor /etc/supervisor/conf.d

# Create supervisor config
RUN echo '[supervisord]' > /etc/supervisor/conf.d/supervisord.conf && \
    echo 'nodaemon=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'user=root' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '[program:postgresql]' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'command=/usr/lib/postgresql/16/bin/postgres -D /var/lib/postgresql/16/main -c config_file=/etc/postgresql/16/main/postgresql.conf' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'user=postgres' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autostart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autorestart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stdout_logfile=/var/log/supervisor/postgresql.log' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stderr_logfile=/var/log/supervisor/postgresql.log' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '[program:elasticsearch]' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'command=/usr/share/elasticsearch/bin/elasticsearch' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'user=elasticsearch' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autostart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autorestart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stdout_logfile=/var/log/supervisor/elasticsearch.log' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stderr_logfile=/var/log/supervisor/elasticsearch.log' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'environment=ES_HOME="/usr/share/elasticsearch",ES_PATH_CONF="/etc/elasticsearch"' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '[program:java-search-service]' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'command=/usr/bin/java -Xms256m -Xmx1024m -XX:+UseParallelGC -XX:ParallelGCThreads=2 -XX:MaxMetaspaceSize=256m -Djava.awt.headless=true -XX:+UseCompressedOops -XX:NewRatio=3 -jar /app/java-search-service.jar' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'directory=/app' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'user=appuser' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autostart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autorestart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stdout_logfile=/var/log/supervisor/java-search-service.log' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stderr_logfile=/var/log/supervisor/java-search-service.log' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'environment=CLIP_TEXT_MODEL_PATH="/app/models/clip-vit-base-patch32-text.onnx",CLIP_IMAGE_MODEL_PATH="/app/models/clip-vit-base-patch32-visual.onnx",CLIP_VOCAB_PATH="/app/models/vocab.txt",CLIP_MERGES_PATH="/app/models/merges.txt",ELASTICSEARCH_HOST="localhost"' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '[program:java-backend]' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'command=/usr/bin/java -Xms128m -Xmx512m -XX:+UseParallelGC -XX:ParallelGCThreads=2 -XX:MaxMetaspaceSize=128m -Djava.awt.headless=true -XX:+UseCompressedOops -jar /app/java-backend.jar' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'directory=/app' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'user=appuser' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autostart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autorestart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stdout_logfile=/var/log/supervisor/java-backend.log' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stderr_logfile=/var/log/supervisor/java-backend.log' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'environment=SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/imagesearch",DB_USERNAME="imageuser",DB_PASSWORD="changeme123",SEARCH_SERVICE_URL="http://localhost:5001",STORAGE_BACKEND="local",IMAGES_ROOT="/app/data/uploads"' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '[program:nginx]' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'command=/usr/sbin/nginx -g "daemon off;"' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autostart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autorestart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stdout_logfile=/var/log/supervisor/nginx.log' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stderr_logfile=/var/log/supervisor/nginx.log' >> /etc/supervisor/conf.d/supervisord.conf

# Create startup script
RUN echo '#!/bin/bash' > /app/startup.sh && \
    echo 'set -e' >> /app/startup.sh && \
    echo '' >> /app/startup.sh && \
    echo '# Initialize PostgreSQL data directory if empty' >> /app/startup.sh && \
    echo 'if [ ! -s "/var/lib/postgresql/16/main/PG_VERSION" ]; then' >> /app/startup.sh && \
    echo '    echo "Initializing PostgreSQL database..."' >> /app/startup.sh && \
    echo '    su postgres -c "/usr/lib/postgresql/16/bin/initdb -D /var/lib/postgresql/16/main"' >> /app/startup.sh && \
    echo '    ' >> /app/startup.sh && \
    echo '    # Start PostgreSQL temporarily to create database' >> /app/startup.sh && \
    echo '    su postgres -c "/usr/lib/postgresql/16/bin/postgres -D /var/lib/postgresql/16/main" &' >> /app/startup.sh && \
    echo '    PG_PID=$!' >> /app/startup.sh && \
    echo '    sleep 10' >> /app/startup.sh && \
    echo '    ' >> /app/startup.sh && \
    echo '    # Create user and database' >> /app/startup.sh && \
    echo '    su postgres -c "psql --command \"CREATE USER imageuser WITH SUPERUSER PASSWORD '\''changeme123'\'';\""' >> /app/startup.sh && \
    echo '    su postgres -c "createdb -O imageuser imagesearch"' >> /app/startup.sh && \
    echo '    ' >> /app/startup.sh && \
    echo '    # Stop temporary PostgreSQL' >> /app/startup.sh && \
    echo '    kill $PG_PID' >> /app/startup.sh && \
    echo '    wait $PG_PID || true' >> /app/startup.sh && \
    echo 'fi' >> /app/startup.sh && \
    echo '' >> /app/startup.sh && \
    echo 'echo "Starting all services with supervisor..."' >> /app/startup.sh && \
    echo 'exec /usr/bin/supervisord -c /etc/supervisor/conf.d/supervisord.conf' >> /app/startup.sh && \
    chmod +x /app/startup.sh

# Expose ports
# 3000: React Frontend (Nginx)
# 8080: Java Backend (Spring Boot)  
# 5001: Java Search Service (ONNX + Elasticsearch)
# 5432: PostgreSQL Database
# 9200: Elasticsearch
EXPOSE 3000 8080 5001 5432 9200

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:3000/ || exit 1

# Set working directory
WORKDIR /app

# Start all services
CMD ["/app/startup.sh"]