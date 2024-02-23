package de.bachelorarbeit.MicroserviceB.Init;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.bachelorarbeit.MicroserviceB.DB2.entities.User;
import de.bachelorarbeit.MicroserviceB.Services.UserRepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.ConnectException;

@Order(-1)
@Component
public class InitRunner implements CommandLineRunner {

    @Value("${spring.db2.jpa.hibernate.ddl-auto}")
    private String DDL_AUTO_DB2;

    @Value("${users.public.keys.filepath}")
    private String publicKeysFilepath;

    @Value(("${users.filepath}"))
    private String usersFilePath;

    private UserRepositoryService userRepositoryService;

    @Autowired
    public InitRunner(UserRepositoryService userRepositoryService) {
        this.userRepositoryService = userRepositoryService;
    }

    Logger logger = LoggerFactory.getLogger(InitRunner.class);

    @Override
    public void run(String... args) throws Exception {

        logger.info("writing users from file into DB2");
        this.writeUsersIntoDB2(usersFilePath);

    }

    private void writeUsersIntoDB2(String usersFilePath){
        try (BufferedReader br = new BufferedReader(new FileReader(usersFilePath))) {

            ObjectMapper objectMapper = new ObjectMapper();

            String userJSON = br.readLine();
            User user;
            while(userJSON != null){
                logger.debug("UserJSON: " + userJSON);
                try {
                    user = objectMapper.readValue(userJSON, User.class);
                }catch (Exception exc){
                    logger.error("Error turning json into user object");
                    exc.printStackTrace();
                    return;
                }
                try{
                    User savedUser = userRepositoryService.save(user);
                    logger.debug("Saved user: " + savedUser + " in DB2 ");
                }catch (ConnectException exc){
                    logger.error("ERROR cannot save user: " + user + " in DB2 because DB2 is down.");
                    return;
                }
                userJSON = br.readLine();
            }

            logger.info("All users read and saved to db.");

        } catch (IOException e) {
            logger.error("Sth wrong with the file to read users from");
            e.printStackTrace();
        }
    }

}
