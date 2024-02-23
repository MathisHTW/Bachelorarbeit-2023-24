#!/bin/bash

# Build and start the Docker Compose services
docker-compose -f ../msb-docker-compose.yml --env-file ../.msb-env build
docker-compose -f ../msb-docker-compose.yml --env-file ../.msb-env up --force-recreate