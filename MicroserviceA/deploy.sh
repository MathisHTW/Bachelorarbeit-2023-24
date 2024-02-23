#!/bin/bash

# Navigate to the directory containing your project
cd /Users/m/Desktop/code-Bachelorarbeit/MicroserviceA

# Run Maven package
mvn package

# Build and start the Docker Compose services
docker-compose -f ../msa-docker-compose.yml --env-file ../.msa-env build
docker-compose -f ../msa-docker-compose.yml --env-file ../.msa-env up --force-recreate
