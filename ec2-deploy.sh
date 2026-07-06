#!/bin/bash
# /opt/blog-api/deploy.sh
# Runs on EC2 — pulls latest code, rebuilds Docker image, restarts container.
# Called by GitHub Actions on every push to main.
set -e

IMAGE="portfolio-blog-api"
CONTAINER="blog-api"
REPO_DIR="/opt/blog-api/repo"

echo "=== $(date) Deploy started ==="

echo "Pulling latest code..."
cd $REPO_DIR
git pull origin main

echo "Building Docker image..."
docker build -t $IMAGE .

echo "Stopping old container (if running)..."
docker stop $CONTAINER 2>/dev/null || true
docker rm   $CONTAINER 2>/dev/null || true

echo "Starting new container..."
docker run -d \
  --name $CONTAINER \
  --env-file /opt/blog-api/.env \
  --restart unless-stopped \
  -p 8080:8080 \
  $IMAGE

echo "Waiting for app to start..."
sleep 12

echo "Health check..."
if curl -sf http://localhost:8080/api/health > /dev/null; then
  echo "✅ Deploy successful"
else
  echo "❌ Health check failed — check: docker logs $CONTAINER"
  exit 1
fi

# Remove dangling images to keep disk clean
docker image prune -f

echo "=== Deploy complete ==="
