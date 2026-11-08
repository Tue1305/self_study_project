#!/bin/bash
set -e

echo "=== Pulling latest changes from Git ==="
git fetch origin main
git reset --hard origin/main

echo "=== Rebuilding and starting app-demo on app-network ==="
docker compose up -d --build app-demo

echo "=== Pruning unused Docker images ==="
docker image prune -f

echo "=== Deployment Complete! ==="
docker ps
