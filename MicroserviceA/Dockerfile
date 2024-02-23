FROM openjdk

COPY target/MicroserviceA-0.0.1-SNAPSHOT.jar msa.jar

RUN mkdir /created-msa-data

CMD java -jar msa.jar