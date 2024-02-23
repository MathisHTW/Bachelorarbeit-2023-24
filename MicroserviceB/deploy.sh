#!/bin/bash

# Navigate to the directory containing your project
cd /Users/m/Desktop/code-Bachelorarbeit/MicroserviceB

# Run Maven package
mvn package

# Build and start the Docker Compose services
docker-compose -f ../msb-docker-compose.yml --env-file ../.msb-env build
docker-compose -f ../msb-docker-compose.yml --env-file ../.msb-env up --force-recreate