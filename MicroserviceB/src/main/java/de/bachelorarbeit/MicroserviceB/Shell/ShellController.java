package de.bachelorarbeit.MicroserviceB.Shell;

import de.bachelorarbeit.MicroserviceB.DB1.entities.Einzelanordnung;
import de.bachelorarbeit.MicroserviceB.DB2.entities.User;
import de.bachelorarbeit.MicroserviceB.Services.AESservice;
import de.bachelorarbeit.MicroserviceB.Services.EinzelanordnungRepositoryService;
import de.bachelorarbeit.MicroserviceB.Services.RSAservice;
import de.bachelorarbeit.MicroserviceB.Services.UserRepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@ShellComponent
public class ShellController {

    AESservice aeSservice;

    RSAservice rsAservice;

    private EinzelanordnungRepositoryService einzelanordnungRepositoryService;

    private UserRepositoryService userRepositoryService;

    @Autowired
    public ShellController(RSAservice rsAservice,AESservice aeSservice, EinzelanordnungRepositoryService einzelanordnungRepositoryService, UserRepositoryService userRepositoryService) {
        this.rsAservice = rsAservice;
        this.aeSservice = aeSservice;
        this.einzelanordnungRepositoryService = einzelanordnungRepositoryService;
        this.userRepositoryService = userRepositoryService;
    }

    Logger logger = LoggerFactory.getLogger(ShellController.class);

    //TESTET OB DIE BEIDEN EIGENEN SCHLÜSSEL ZUEINANDER PASSEN:
    //TESTET OB DIE BEIDEN EIGENEN SCHLÜSSEL ZUEINANDER PASSEN:
    //für 128 und 256 sicherheitsniveau
    @ShellMethod(key="testEncDec")
    public String testKeys2(){

        String result = "";

        String testPlaintext = "hello there";

        //Test für 128:
        //Verschlüsseln mit dem eigenen 3072 public key
        byte[] ciphertext = rsAservice.encrypt(testPlaintext.getBytes(),rsAservice.getPublicKey3072());

        //entschlüsseln mit dem dazu passenden private key
        byte[] decryptedCiphertext = rsAservice.decrypt(ciphertext,128);
        String decryptedString = new String(decryptedCiphertext, StandardCharsets.UTF_8);

        if(decryptedString.equals(testPlaintext)) result += "Eigene Schlüssel passen zueinander (128)\n";
        else{
            result += "Etwas stimmt mit den Schlüsseln / der Verschlüsselung der Entschlüsselung nicht!(128)\n";
        }


        //TODO um dies nutzen zu können müssen 15360 Schlüssel generiert werden

        //Test für 256:
        //Verschlüsseln mit dem eigenen 15360 public key
        ciphertext = rsAservice.encrypt(testPlaintext.getBytes(),rsAservice.getPublicKey15360());

        //entschlüsseln mit dem dazu passenden private key
        decryptedCiphertext = rsAservice.decrypt(ciphertext,256);
        decryptedString = new String(decryptedCiphertext, StandardCharsets.UTF_8);

        if(decryptedString.equals(testPlaintext)) result += "Eigene Schlüssel passen zueinander (256)\n";
        else{
            result += "Etwas stimmt mit den Schlüsseln / der Verschlüsselung der Entschlüsselung nicht!(256)\n";
        }


        return result;

    }

    //ES MUSS VORHER BEI MSA MINDESTENS GEN SESSION KEY AUSGEFÜHRT WORDEN SEIN
    //TEST METHODE UM ZU SEHEN OB DER AES SESSIONKEY KORREKT GESETZT wurde durch msa UND ZUM VER UND ENTSCHLÜSSELN IN AES VEWENDET WERDEN KANN
    @ShellMethod(key="testNewSessionkey")
    public String testNewSessionKey() throws Exception{
        logger.info("neu gesetzter aes sessionkey als base64 String: " + Base64.getEncoder().encodeToString(aeSservice.getSessionKey().getEncoded()));

        String plaintext = "hoffentlich funktioniert das";
        byte[] associatedData = "bitte bitte".getBytes();
        byte[] ciphertext = aeSservice.encrypt(plaintext,associatedData);
        String decryptedCipherText = aeSservice.decrypt(ciphertext,associatedData);

        if(decryptedCipherText.equals(plaintext)) return "Das Ver und Entschlüsseln mit dem durch RSA generierten AES session key funktioniert!";
        return "Etwas stimmt nicht mit dem neu erzeugten session key!";
    }

    //get new sessionkey as string
    @ShellMethod(key="getSessionKey")
    public String printSessionKey(){
        return "current session key: " + Base64.getEncoder().encodeToString(aeSservice.getSessionKey().getEncoded());
    }

    @ShellMethod(key="user-find-all")
    public void findallusers(){
        List<User> allFoundUser;
        try {
            allFoundUser = userRepositoryService.findAll();
        } catch (ConnectException e) {
            logger.error("Could not execute findall on userRepository");
            return;
        }
        for(User user: allFoundUser){
            logger.info(user.toString());
        }
    }

    @ShellMethod(key="utest-userrepository-methods-for-robustnes")
    public void testuserrepositoryservice(){
        List<User> allFoundUsers = null;
        try {
            allFoundUsers = userRepositoryService.findAll();
        } catch (ConnectException e) {
            logger.error("Could not execute findall for users properly");
            return;
        }

        for(User user: allFoundUsers){
            logger.info(user.toString());
        }

        User newUser = new User("Hello","There");
        User savedUser;
        try {
            savedUser = userRepositoryService.save(newUser);
            logger.info("Saved user: " + savedUser);
        } catch (ConnectException e) {
            logger.error("Could not execute save for users properly");
            return;
        }

        try {
            logger.info("Id of saved user (testing find by id): " + userRepositoryService.findById(savedUser.getId()));
        } catch (ConnectException e) {
            logger.error("Could not execute findbyId for users properly");
        }
    }

    @ShellMethod(key="utest-findall")
    public void testFindallUsers(){
        List<User> allFoundUsers = null;
        try {
            allFoundUsers = userRepositoryService.findAll();
        } catch (ConnectException e) {
            logger.error("Could not execute findall for users properly");
            return;
        }

        for(User user: allFoundUsers){
            logger.info(user.toString());
        }
    }

    @ShellMethod(key="utest-saveUser")
    public void saveUser(){
        User newUser = new User("Hello","There");
        User savedUser;
        try {
            savedUser = userRepositoryService.save(newUser);
            logger.info("Saved user: " + savedUser);
        } catch (ConnectException e) {
            logger.error("Could not execute save for users properly");
        }
    }
    @ShellMethod(key="utest-idFind")
    public void findUserById(int id){
        try {
            logger.info("Id of saved user (testing find by id): " + userRepositoryService.findById(id));
        } catch (ConnectException e) {
            logger.error("Could not execute findbyId for users properly");
        }
    }

    @ShellMethod(key="etest-save")
    public void testSaveOfEinzelanordnungRepositoryService(){
        Einzelanordnung testEinzelanordnung = Einzelanordnung.generateTestEinzelanordnung();

        Einzelanordnung savedEinzelanordnung;
        try {
            savedEinzelanordnung = einzelanordnungRepositoryService.save(testEinzelanordnung);
        } catch (ConnectException e) {
            logger.error("could not execute save of einzelanordnung");
            return;
        }

        if(savedEinzelanordnung == null){
            logger.error("could not save einzelanordnung with id: " + testEinzelanordnung.getId());
        }
    }

    @ShellMethod(key="etest-Idfind")
    public void testfindByIdOfEinzelanordnungRepositoryService(int id){
        Einzelanordnung foundEinzelanordnung;
        try {
            foundEinzelanordnung = einzelanordnungRepositoryService.findById(id);
        } catch (ConnectException e) {
            logger.error("Error could not execute findById properly");
            return;
        }

        if(foundEinzelanordnung == null){
            logger.info("Could not find Einzelanordnung with id: " + id + " in DB1");
            return;
        }

        logger.info("Found einzelanordnung in DB1: " + foundEinzelanordnung);
    }

    @ShellMethod(key="etest-findAll")
    public void testfindAllOfEinzelanordnungRepositoryService(){
        List<Einzelanordnung> foundEinzelanordnungen;
        try {
            foundEinzelanordnungen = einzelanordnungRepositoryService.findAll();
        } catch (ConnectException e) {
            logger.error("Could not execute findall properly");
            return;
        }

        for(Einzelanordnung einzelanordnung: foundEinzelanordnungen){
            logger.info(einzelanordnung.toString());
        }

    }




}
