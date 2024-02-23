# Bachelorarbeit

# Dependencies:
1. Docker

# Run MSA and MSB on the same host

1. Clone the repository.
2. cd into MicroserviceA and run ```./setup.sh``` and choose the amount of users. Wait until its done.
3. cd into MicroserviceB and run ```./setup.sh```. Wait until its done.
4. run ```./run.sh <delay for response in ms> <amount of MSB instances>```. Example: ```./run.sh 60 2```.
5. You might need to restart a Instance of MSB if you are running on Windows.
6. cd into MicroserviceA and run ```./run.sh <IP of your host within your LAN. NOT LOCALHOST!> <security level, 128 or 256> <failure rate for messages in %> <amount of messages>```. Example: ```./run.sh 192.168.178.155 256 2 100000```.

# Run MSA and MSB on different hosts

## MSA HOST:
1. Clone the repository.
2. run ```./setup.sh``` and choose the amount of users. Wait until its done.
3. copy the created file user.txt from created-msa-files into the same folder on the Host of MSB.
4. Wait until you ran the run command on MSB HOST.
5. Get the Ip of the MSB Host.
6. run ```./run.sh <IP of the Host of MSB> <security level, 128 or 256> <failure rate for messages in %> <amount of messages>```. Example: ```./run.sh 192.168.178.24 256 2 100000```.

## MSB HOST:
1. Clone the repository
2. copy the created file user.txt from created-msa-files on Host of MSA into created-msa-files on this host.
3. cd into MicroserviceB and run ```./setup.sh```. Wait until its done.
4. run ```./run.sh <delay for response in ms> <amount of MSB instances>```. Example: ```./run.sh 60 2```.
5. You might need to restart an Instance of MSB if you are running on Windows.

# Cleanup
To run the next test the Database 1 of MSA and MSB has to be wiped of old entries.
For that cd into MicroserviceA/MicroserviceB and execute ```./clean-db1.sh```

**Be aware the volume for DB1 can become quite large. This can lead to issues.** 
