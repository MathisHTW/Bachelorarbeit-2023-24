package de.bachelorarbeit.MicroserviceA.TestService;

import de.bachelorarbeit.MicroserviceA.DB1.entities.Einzelanordnung;
import de.bachelorarbeit.MicroserviceA.DB2.entities.User;
import de.bachelorarbeit.MicroserviceA.Init.InitRunner;
import de.bachelorarbeit.MicroserviceA.Services.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class TestService {

    SendMessageService sendMessageService;

    EinzelanordnungGenerator einzelanordnungGenerator;

    EinzelanordnungRepositoryService einzelanordnungRepositoryService;

    UserRepositoryService userRepositoryService;

    Logger logger = LoggerFactory.getLogger(TestService.class);

    @Autowired
    public TestService(SendMessageService sendMessageService, EinzelanordnungGenerator einzelanordnungGenerator,EinzelanordnungRepositoryService einzelanordnungRepositoryService, UserRepositoryService userRepositoryService) {
        this.sendMessageService = sendMessageService;
        this.einzelanordnungGenerator = einzelanordnungGenerator;
        this.einzelanordnungRepositoryService = einzelanordnungRepositoryService;
        this.userRepositoryService = userRepositoryService;
    }

    /**
     * Tests the time it takes to generate, send and receive the given number of messages
     * @param sicherheitsniveau
     * @param msgCount
     * @return long that tells how many milliseconds the test took
     * @throws ConnectException if DB1 cannot be checked to see if messages were received or not
     */
    public long testSpeed(int sicherheitsniveau, int msgCount) throws ConnectException {

        //long startTime = System.currentTimeMillis();

        //rsAservice.keyEstablishment(sicherheitsniveau);
        this.generateSendWaitForEinzelanordnung(msgCount);

        long endTime = System.currentTimeMillis();

        return endTime; //- startTime;
    }

    private void generateSendWaitForEinzelanordnung(int msgCount) throws ConnectException {

        if(msgCount == 0) return;

        this.generateAndSendEinzelanordnungen(msgCount);
        //wait until all send messages were marked as received by msb in msa DB1
        while(einzelanordnungRepositoryService.findById(msgCount)== null || !einzelanordnungRepositoryService.findAllNotReceived().isEmpty()){
            logger.debug("Waiting for all messages to be marked as received");
        }


    }

    /**
     * Initialsizes the DB1 with users
     * then generates Anordnungen and sends them to MSB
     * @param amountOfEinzelanordnungen
     *
     */
    public void generateAndSendEinzelanordnungen(int amountOfEinzelanordnungen){

        double betrag = 1.0;
        String empfaenger="Empfänger-";

        Random rand = new Random();
        int pruefer1Id;
        int pruefer2Id;

        List<Integer> userIds;
        try {
            userIds = userRepositoryService.findAll().stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
        } catch (ConnectException e) {
            logger.error("Could not generate and send messages because DB2 is not available");
            return;
        }
        int userIdsSize = userIds.size();

        Einzelanordnung generatedEinzelanordnung;
        for(int i = 0; i < amountOfEinzelanordnungen; ++i){
            int randomIndexP1 = rand.nextInt(userIdsSize);
            int randomIndexP2 = rand.nextInt(userIdsSize);

            //generate Einzelanordnung
            pruefer1Id = userIds.get(randomIndexP1);
            pruefer2Id = userIds.get(randomIndexP2);
            //todo die Anordnung muss in dem Constructor unterschrieben werden
            generatedEinzelanordnung = einzelanordnungGenerator.generateSignedEinzelanordnung(betrag+i,empfaenger+i,pruefer1Id,pruefer2Id);
            //send it to MSB2 and update status in db if successfull
            if(generatedEinzelanordnung != null){//passiert wenn die anordnung nicht unterschrieben oder gespeichert werden konnte
                sendMessageService.sendLoadbalanced(generatedEinzelanordnung.getId());
            }

        }

    }

    public void generateAndSendEinzelanordnungen(int amountOfEinzelanordnungen, String syncStatus){

        double betrag = 1.0;
        String empfaenger="Empfänger-";

        Random rand = new Random();
        int pruefer1Id;
        int pruefer2Id;

        List<Integer> userIds;
        try {
            userIds = userRepositoryService.findAll().stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
        } catch (ConnectException e) {
            logger.error("Could not generate and send messages because DB2 is not available");
            return;
        }
        int userIdsSize = userIds.size();

        Einzelanordnung generatedEinzelanordnung;

        if(syncStatus.equals("async")){
            for(int i = 0; i < amountOfEinzelanordnungen; ++i){
                //generate Einzelanordnung
                int randomIndexP1 = rand.nextInt(userIdsSize);
                int randomIndexP2 = rand.nextInt(userIdsSize);

                //generate Einzelanordnung
                pruefer1Id = userIds.get(randomIndexP1);
                pruefer2Id = userIds.get(randomIndexP2);
                //todo die Anordnung muss in dem Constructor unterschrieben werden
                generatedEinzelanordnung = einzelanordnungGenerator.generateSignedEinzelanordnung(betrag+i,empfaenger+i,pruefer1Id,pruefer2Id);
                //send it to MSB2 and update status in db if successfull
                if(generatedEinzelanordnung != null){//passiert wenn die anordnung nicht unterschrieben oder gespeichert werden konnte
                    sendMessageService.sendLoadbalanced(generatedEinzelanordnung.getId());
                }

            }
        }
        else{
            for(int i = 0; i < amountOfEinzelanordnungen; ++i){
                //generate Einzelanordnung
                int randomIndexP1 = rand.nextInt(userIdsSize);
                int randomIndexP2 = rand.nextInt(userIdsSize);

                //generate Einzelanordnung
                pruefer1Id = userIds.get(randomIndexP1);
                pruefer2Id = userIds.get(randomIndexP2);
                //todo die Anordnung muss in dem Constructor unterschrieben werden
                generatedEinzelanordnung = einzelanordnungGenerator.generateSignedEinzelanordnung(betrag+i,empfaenger+i,pruefer1Id,pruefer2Id);
                //send it to MSB2 and update status in db if successfull
                if(generatedEinzelanordnung != null){//passiert wenn die anordnung nicht unterschrieben oder gespeichert werden konnte
                    sendMessageService.sendLoadbalanced(generatedEinzelanordnung.getId());
                }
            }
        }


    }
}
