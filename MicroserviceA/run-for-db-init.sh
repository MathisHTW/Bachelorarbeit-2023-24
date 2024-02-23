#!/bin/bash

# Build and start the Docker Compose services
docker-compose -f ../msa-docker-compose.yml --env-file ../.msa-env build
docker-compose -f ../msa-docker-compose.yml --env-file ../.msa-env up --force-recreate
