#!/bin/bash

if [ $# -lt 2 ]; then
    echo "Usage: $0 <delay for messages in ms> <amount of instances as positive int> <failure chance for service> <time interval for failure chance in seconds>"
    exit 1
else
  delay=$1
  instances=$2
  service_failure_chance=$3
  failure_time_interval=$4
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
if ! [[ $service_failure_chance =~ ^[1-9][0-9]?$|100$ ]]; then
  service_failure_chance=1
  echo "service failure chance set to default 1%"
fi
if ! [[ "$failure_time_interval" =~ ^[1-9][0-9]*$ ]]; then
  failure_time_interval=5
  echo "time interval set to default 5 seconds"
fi

containers=("code-bachelorarbeit-mysqlusermsb-1" "code-bachelorarbeit-mysqleinzelanordnungmsb-1")
for ((i=1; i<=$instances; i++)); do
    containers+=("code-bachelorarbeit-msb-$i")
done

. ./run.sh $delay $instances &

sleep 10

while true; do
    # Randomly select a container with a 1% chance
    if [ $((RANDOM % 100)) -lt $service_failure_chance ]; then
        # Randomly choose an index from the array
        random_index=$((RANDOM % ${#containers[@]}))

        # Get the container name
        container_name=${containers[$random_index]}

        # Stop the selected container
        docker stop $container_name

        echo "Stopped container: $container_name"

        # Wait for 10 seconds
        sleep 10

        # Start the container again
        docker start $container_name

        echo "Started container: $container_name"
    else
        echo "No action taken."
    fi

    # Sleep for 2 seconds before the next iteration
    sleep $failure_time_interval
done
