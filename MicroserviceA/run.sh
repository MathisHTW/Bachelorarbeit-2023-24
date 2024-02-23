#!/bin/bash

# Check if at least one argument is provided

if [ $# -lt 3 ]; then
    echo "Usage: $0 <msb ipv4 address> <security level 128 or 256> <integer drop rate messages from msa to msb> <amount of messages>"
    exit 1
else
  msb_ip_address=$1
  security_level=$2
  drop_rate=$3
  amount_of_messages=$4
fi

# Access the first argument using $1
if [[ ! $msb_ip_address =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
    echo "Invalid IPv4 address format: $ip_address"
    exit 1
fi
if [[ $security_level -ne 128 && $security_level -ne 256 ]]; then
    echo "Invalid input: $security_level is not 128 or 256"
    security_level=128
    echo "security level set to default 128"
fi
if ! [[ $drop_rate =~ ^[1-9][0-9]?$|100$ ]]; then
  drop_rate=2
  echo "drop rate set to default 2%"
fi
if ! [[ "$amount_of_messages" =~ ^[1-9][0-9]*$ ]]; then
  amount_of_messages=0
  echo "no messages will be sent on start. You can send messages using ./attach"
fi

file_path=../.msa-env

if [ -f "$file_path" ]; then
    sed -i.bakup 's/DDL_AUTO_EINZELANORDNUNG.*/DDL_AUTO_EINZELANORDNUNG=none/' "$file_path"
    sed -i.bakup 's/DDL_AUTO_USER.*/DDL_AUTO_USER=none/' "$file_path"
    sed -i.bakup "s/HOST_IP_V4_ADDRESS.*/HOST_IP_V4_ADDRESS=${msb_ip_address}/" "$file_path"
    sed -i.bakup "s/SECURITY_LEVEL.*/SECURITY_LEVEL=${security_level}/" "$file_path"
    sed -i.bakup "s/DROP_RATE.*/DROP_RATE=${drop_rate}/" "$file_path"
    sed -i.bakup "s/AMOUNT_OF_MESSAGES.*/AMOUNT_OF_MESSAGES=${amount_of_messages}/" "$file_path"
else
    echo "File $file_path does not exist."
fi

docker-compose -f ../msa-docker-compose.yml --env-file ../.msa-env build
docker-compose -f ../msa-docker-compose.yml --env-file ../.msa-env up --force-recreate