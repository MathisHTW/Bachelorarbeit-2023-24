#!/bin/bash

docker ps

# Find the container ID for the container containing 'msa-1' in its name
container_id=$(docker ps | grep "code-bachelorarbeit-msa-1" | awk '{print $1;}')
echo $container_id

#If container_id is not empty, attach to the container
if [ -n "$container_id" ]; then
    docker attach $container_id
else
    echo "Container with 'code-bachelorarbeit-msa-1' not found."
fi