#!/bin/bash

#needed to create the tables within the Databases

#Erzeugt tabelle für Einzelanordnung
#Erzeugt tabelle für User
#Erzeugt default user in User DB
#Schreibt die erzeugten User als JSON in eine TextDatei

# File path

file_path=../.msa-env

read -p "Enter the amount of users: " user_amount
if [[ "$user_amount" =~ ^[1-9][0-9]*$ ]]; then
  sed -i.bakup "s/AMOUNT_OF_USERS.*/AMOUNT_OF_USERS=${user_amount}/" "$file_path"
else
  echo "Default number of users = 100."
  sed -i.bakup 's/AMOUNT_OF_USERS.*/AMOUNT_OF_USERS=100/' "$file_path"
fi

if [ -f "$file_path" ]; then
  sed -i.bakup 's/DDL_AUTO_EINZELANORDNUNG.*/DDL_AUTO_EINZELANORDNUNG=create/' "$file_path"
  sed -i.bakup 's/DDL_AUTO_USER.*/DDL_AUTO_USER=create/' "$file_path"
  echo "DDL_AUTO set to create."
else
    echo "File $file_path does not exist."
fi

. ./run-for-db-init.sh &

sleep_duration=$((user_amount + 20))
sleep "$sleep_duration"

. ./clean-docker.sh