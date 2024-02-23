package de.bachelorarbeit.MicroserviceA.Init;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.bachelorarbeit.MicroserviceA.DB1.entities.Einzelanordnung;
import de.bachelorarbeit.MicroserviceA.DB2.entities.User;
import de.bachelorarbeit.MicroserviceA.Services.EinzelanordnungGenerator;
import de.bachelorarbeit.MicroserviceA.Services.RSAservice;
import de.bachelorarbeit.MicroserviceA.Services.SendMessageService;
import de.bachelorarbeit.MicroserviceA.Services.UserRepositoryService;
import de.bachelorarbeit.MicroserviceA.TestService.TestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Order(-1)//soll vor der spring shell ausgeführt werden
@Component
public class InitRunner implements CommandLineRunner {

    @Value("${spring.db2.jpa.hibernate.ddl-auto}")
    private String DDL_AUTO_DB2;

    @Value("${amount.of.users}")
    private int amountOfUsers;

    @Value("${users.public.keys.filepath}")
    private String publicKeysFilepath;

    @Value("${users.filepath}")
    private String usersFilePath;

    @Value("${security.level}")
    private int sicherheitsniveau;

    @Value("${amount.of.messages}")
    private int amountOfMessages;

    private UserRepositoryService userRepositoryService;

    private EinzelanordnungGenerator einzelanordnungGenerator;

    private RSAservice rsAservice;

    private final ScheduledExecutorService schedulerNotReceived = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService schedulerNotSent = Executors.newSingleThreadScheduledExecutor();

    private final ScheduledExecutorService shedulerRenewSessionKeys = Executors.newSingleThreadScheduledExecutor();

    private SendMessageService sendMessageService;

    private TestService testService;

    @Autowired
    public InitRunner(UserRepositoryService userRepositoryService, RSAservice rsAservice, SendMessageService sendMessageService, EinzelanordnungGenerator einzelanordnungGenerator, TestService testService) {
        this.userRepositoryService = userRepositoryService;
        this.rsAservice = rsAservice;
        this.sendMessageService = sendMessageService;
        this.einzelanordnungGenerator = einzelanordnungGenerator;
        this.testService = testService;
    }

    Logger logger = LoggerFactory.getLogger(InitRunner.class);

    @Override
    public void run(String... args) throws Exception {

        //generates Amount of users in DB2
        setupDataBases(amountOfUsers);

        //Schreibe alle User aus DB2 in eine Datei welche von MSB verwendet werden kann um seine DB2
        //zu initialisieren
        this.writeUsersToFile();

        //Dont execute anything else in the setup process
        if(DDL_AUTO_DB2.equals("create")){
            logger.info("DB1 and DB2 were Created.");
            logger.info("DB2 was filled with " + amountOfUsers + " users");
            return;
        }

        //this.generateRemainingEinzelanordnungenInDB1(50);

        long startTime = System.currentTimeMillis();
        long startTimeKeyestablishment = startTime;

        rsAservice.keyEstablishment(this.sicherheitsniveau);

        long endTimeKeyEstablishment = System.currentTimeMillis();
        long elapsedTimeForKeyEstablishment = endTimeKeyEstablishment - startTimeKeyestablishment;

        schedulerNotReceived.scheduleAtFixedRate(() -> {
            try {
                sendMessageService.sendLoadbalancedRemaining();
            }catch(Exception exc) {
                logger.error("ERROR send Remaining messages could not be executed");
            }
        }, 60, 60, TimeUnit.SECONDS);

        schedulerNotSent.scheduleAtFixedRate(()->{
            try{
                einzelanordnungGenerator.signUnsigned();
                einzelanordnungGenerator.saveUnsaved();
                sendMessageService.flagUnflagged();
                sendMessageService.updateNotUpdated();
            }catch (Exception exc){
                logger.error("ERROR signing or saving messages could not be executed");
            }
        },20,20,TimeUnit.SECONDS);

        logger.info("-------------------------------------------------------------------------------------------------------------------");
        logger.info("Start speed test:");
        logger.info("-------------------------------------------------------------------------------------------------------------------");

        //Test the speed to establish the connection with all active ports of msb
        //and to generate send receive and update messagesCnt messages
        long endTime = testService.testSpeed(sicherheitsniveau,amountOfMessages);
        long elapsedTime = endTime - startTime;

        logger.info("-------------------------------------------------------------------------------------------------------------------");
        logger.info("-------------------------------------------------------------------------------------------------------------------");
        logger.info("-------------------------------------------------------------------------------------------------------------------");
        logger.info("It took: " + elapsedTime + "msq to send, save and receive the response for: " + amountOfMessages + " messages");
        logger.info("From that It took: " + elapsedTimeForKeyEstablishment + "ms to do the keyestablishment");
        logger.info("-------------------------------------------------------------------------------------------------------------------");
        logger.info("-------------------------------------------------------------------------------------------------------------------");
        logger.info("-------------------------------------------------------------------------------------------------------------------");

        //Es werden zum Beginn des nächsten Tages immer neue Sessionkeys ausgehandelt

        shedulerRenewSessionKeys.scheduleAtFixedRate(() ->{
            rsAservice.renewSessionKeys(sicherheitsniveau);
        },1,1,TimeUnit.DAYS);


    }

    private void generateRemainingEinzelanordnungenInDB1(int amount) {

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
            logger.error("Could not create remaining messages in DB1 because DB2 is not available");
            return;
        }
        int userIdsSize = userIds.size();

        //generate Einzelanordnung sign it and save it in the db
        for(int i = 0 ; i < amount; ++i){
            int randomIndexP1 = rand.nextInt(userIdsSize);
            int randomIndexP2 = rand.nextInt(userIdsSize);

            pruefer1Id = userIds.get(randomIndexP1);
            pruefer2Id = userIds.get(randomIndexP2);
            einzelanordnungGenerator.generateSignedEinzelanordnung(betrag+i,empfaenger+i,pruefer1Id,pruefer2Id);
        }


    }

    /**
     * Muss genau gleich sein zu MSA
     * @param amountOfUsers
     */
    private void generateUsersInDB2(int amountOfUsers){
        String firstName = "firstName-";
        String lastName = "lastName-";
        String password = "password-";

        User user;
        for(int i = 1; i <= amountOfUsers; ++i){
            user = new User(firstName+i,lastName+i,password+i); //die keys werden im Constructor generiert
            User savedUser = null;
            try {
                savedUser = userRepositoryService.save(user);
            } catch (ConnectException e) {
                logger.error("ERROR Could not save user:"+ user +" in DB2");
                continue;
            }
            logger.debug("Saved user in DB2: " + savedUser.toString());
        }
    }

    /**
     * Schreibt die Base64 encoded public keys der ersten 1 - amountOfUsers User aus der DB zeilenweise in
     * einen File, der von MSB für die Synchronisation der public Keys der Prüfer gelesen wird
     */
    private void writePublicKeysToFile() {

        String filePath = publicKeysFilepath;

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            User foundUser;
            String foundUserPublicKeyString;
            // Write each line to the file
            for(int i = 1; i <= amountOfUsers; ++i){
                try{
                    foundUser = userRepositoryService.findById(i);
                    if(foundUser == null){
                        logger.error("Could not find User with id: " + i + " in DB2");
                        foundUserPublicKeyString= i + " :------ERROR--------XXX--------ERROR--------";
                    }
                    else{
                        foundUserPublicKeyString = foundUser.getPublicKeyString();
                    }
                }catch(ConnectException exc){
                    logger.error("Error could not access DB2 to write public keys of users to shared file for MSB");
                    foundUserPublicKeyString= i + " :------ERROR--------XXX--------ERROR--------";
                }
                bw.write(foundUserPublicKeyString + "\n");
            }
            logger.info("Wrote Public keys to the file successfully.");
        } catch (IOException e) {
            logger.error("Could not access file to write the public keys");
            //e.printStackTrace();
        }

    }

    private void writeUsersToFile() {
        String filePath = usersFilePath;

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            List<User> allUsers = new ArrayList<>();
            try {
                allUsers = userRepositoryService.findAll();
            } catch (ConnectException exc) {
                logger.error("ERROR COULD NOT ACCESS DB2 TO FIND ALL USERS");
                logger.error("ERROR CANNOT WRITE USERS INTO FILE FOR MSB");
                logger.info("trying again in 10 seconds");
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    //throw new RuntimeException(e);
                }
                this.writeUsersToFile();
            }

            for (User user : allUsers) {
                String userAsJSONstring = this.convertUserToJson(user);
                bw.write(userAsJSONstring + "\n");

            }

        } catch (IOException e) {
            logger.error("Could not access file to write the public keys");
            logger.info("trying again in 10 seconds");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException exc) {
                //throw new RuntimeException(e);
            }
            this.writeUsersToFile();
        }

        logger.info("Wrote Users from DB2 to file: " + filePath);

    }

    private String convertUserToJson(User user) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(user);
        } catch (Exception e) {
            logger.error("Error turning User into Json string");
            logger.info("trying again in 10 seconds");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException exc) {
                logger.info("interrupted waiting time: " + exc.getMessage());
                //throw new RuntimeException(e);
            }

            return this.convertUserToJson(user);
        }

    }

    private void setupDataBases(int amountOfUsers){
        if(DDL_AUTO_DB2.equals("create")){
            logger.info("initializing DB2 with " + amountOfUsers + " users");
            this.generateUsersInDB2(amountOfUsers);
        }
    }


}
