#!/bin/bash

# Check if at least one argument is provided

if [ $# -lt 2 ]; then
    echo "Usage: $0 <delay for messages in ms> <amount of instances as positive int>"
    exit 1
else
  delay=$1
  instances=$2
fi

if ! [[ "$delay" =~ ^[1-9][0-9]*$ ]]; then
  delay=60
  echo "delay set to default: 60ms"
fi
if ! [[ $instances =~ ^[1-9]$|9$ ]]; then
  instances=2
  echo "Invalid input: $input is not a number between 1 and 9"
  echo "instances set to default 2"

fi

file_path=../.msb-env

if [ -f "$file_path" ]; then
    sed -i.bakup 's/DDL_AUTO_EINZELANORDNUNG=.*/DDL_AUTO_EINZELANORDNUNG=none/' "$file_path"
    sed -i.bakup 's/DDL_AUTO_USER=.*/DDL_AUTO_USER=none/' "$file_path"
    sed -i.bakup "s/DELAY=.*/DELAY=${delay}/" "$file_path"
    sed -i.bakup "s/MSB_INSTANCES_CNT=.*/MSB_INSTANCES_CNT=${instances}/" "$file_path"
    sed -i.bakup "s/MSB_HOST_PORT_RANGE_END=.*/MSB_HOST_PORT_RANGE_END=808${instances}/" "$file_path"
else
    echo "File $file_path does not exist."
fi

docker-compose -f ../msb-docker-compose.yml --env-file ../.msb-env build
docker-compose -f ../msb-docker-compose.yml --env-file ../.msb-env up --force-recreate