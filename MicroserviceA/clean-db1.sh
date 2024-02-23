file_path=../.msa-env

if [ -f "$file_path" ]; then
  sed -i.bakup "s/AMOUNT_OF_MESSAGES.*/AMOUNT_OF_MESSAGES=0/" "$file_path"
  sed -i.bakup 's/DDL_AUTO_EINZELANORDNUNG.*/DDL_AUTO_EINZELANORDNUNG=create/' "$file_path"
else
    echo "File $file_path does not exist."
fi

. ./run-for-db-init.sh &

sleep_duration=20

sleep "$sleep_duration"

. ./clean-docker.sh