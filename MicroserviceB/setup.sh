#!/bin/bash

#needed to create the tables within the Databases

#Erzeugt tabelle für Einzelanordnung
#Erzeugt tabelle für User
#Erzeugt default user in User DB
#Schreibt die erzeugten User als JSON in eine TextDatei

# File path

file_path=../.msb-env

if [ -f "$file_path" ]; then
  sed -i.bakup 's/DDL_AUTO_EINZELANORDNUNG.*/DDL_AUTO_EINZELANORDNUNG=create/' "$file_path"
  sed -i.bakup 's/DDL_AUTO_USER.*/DDL_AUTO_USER=create/' "$file_path"
  sed -i.bakup "s/MSB_INSTANCES_CNT=.*/MSB_INSTANCES_CNT=1/" "$file_path"
  sed -i.bakup "s/MSB_HOST_PORT_RANGE_END=.*/MSB_HOST_PORT_RANGE_END=8081/" "$file_path"
else
    echo "File $file_path does not exist."
fi

. ./run-for-db-init.sh &

sleep_duration=40
sleep "$sleep_duration"

. ./clean-docker.sh