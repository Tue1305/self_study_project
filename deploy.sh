#!/bin/bash
set -e

APP_NAME="app-demo"
PORT="8080"

echo "=== Pulling latest changes from Git ==="
git fetch origin main
git reset --hard origin/main

echo "=== Building Docker Image ==="
docker build -t $APP_NAME:latest .

echo "=== Stopping existing container if running ==="
docker rm -f $APP_NAME 2>/dev/null || true

# Detect the Docker network that the 'postgres' container is attached to
PG_NET=$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' postgres 2>/dev/null | awk '{print $1}')

echo "=== Starting new container ==="
if [ -n "$PG_NET" ]; then
    echo "Connecting $APP_NAME to Postgres network: $PG_NET"
    docker run -d --name $APP_NAME --restart always --network "$PG_NET" -p $PORT:$PORT --add-host=host.docker.internal:host-gateway $APP_NAME:latest
else
    docker run -d --name $APP_NAME --restart always -p $PORT:$PORT --add-host=host.docker.internal:host-gateway $APP_NAME:latest
fi

echo "=== Pruning unused Docker images ==="
docker image prune -f

echo "=== Deployment Complete! ==="
docker ps
