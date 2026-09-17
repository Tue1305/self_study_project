#!/bin/bash
set -e

APP_NAME="app-demo"
PORT="8080"

echo "=== Pulling latest changes from Git ==="
git pull origin main

echo "=== Building Docker Image ==="
docker build -t $APP_NAME:latest .

echo "=== Stopping existing container if running ==="
if [ "$(docker ps -q -f name=$APP_NAME)" ]; then
    docker stop $APP_NAME
    docker rm $APP_NAME
fi

echo "=== Starting new container ==="
docker run -d --name $APP_NAME -p $PORT:$PORT --add-host=host.docker.internal:host-gateway $APP_NAME:latest

echo "=== Pruning unused Docker images ==="
docker image prune -f

echo "=== Deployment Complete! ==="
