#!/bin/bash

# Stop all running containers
docker stop $(docker ps -aq)

# Remove all containers
docker rm $(docker ps -aq)

# Down all Docker Compose services and remove volumes
docker-compose down -v

echo "Still Running Docker Container: "

docker ps  