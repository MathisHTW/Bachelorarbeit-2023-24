package de.bachelorarbeit.MicroserviceA.Shell;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.bachelorarbeit.MicroserviceA.DB1.entities.Einzelanordnung;
import de.bachelorarbeit.MicroserviceA.DB2.entities.User;
import de.bachelorarbeit.MicroserviceA.Model.MsaMsbConnectionDetails;
import de.bachelorarbeit.MicroserviceA.Services.*;
import de.bachelorarbeit.MicroserviceA.TestService.TestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.net.ConnectException;
import java.security.*;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@ShellComponent
public class ShellController {

    ObjectMapper objectMapper;

    AESservice aeSservice;

    GeneralSendService generalSendService;

    SendMessageService sendMessageService;

    RSAservice rsAservice;

    EinzelanordnungRepositoryService einzelanordnungRepositoryService;

    UserRepositoryService userRepositoryService;


    TestService testService;

    EinzelanordnungGenerator einzelanordnungGenerator;

    Logger logger = LoggerFactory.getLogger(ShellController.class);

    @Autowired
    public ShellController(SendMessageService sendMessageService, RSAservice rsAservice, GeneralSendService generalSendService, AESservice aeSservice, ObjectMapper objectMapper, EinzelanordnungRepositoryService einzelanordnungRepositoryService, UserRepositoryService userRepositoryService, TestService testService, EinzelanordnungGenerator einzelanordnungGenerator) {
        this.sendMessageService = sendMessageService;
        this.rsAservice = rsAservice;
        this.generalSendService = generalSendService;
        this.aeSservice = aeSservice;
        this.objectMapper = objectMapper;
        this.einzelanordnungRepositoryService=einzelanordnungRepositoryService;
        this.userRepositoryService = userRepositoryService;
        this.testService = testService;
        this.einzelanordnungGenerator = einzelanordnungGenerator;
    }

    /*
    @ShellMethod(key="send")
    public void sendEinzelanordnung(long id, String portNumber){

        //TODO diesen Teil entfernen, und anstelle die Einzelanordnung anhand der id aus der DB holen
        //nur zum Testen
        Einzelanordnung testEinzelanordnung = new Einzelanordnung("Prüfer1","Prüfer2",9999.00,"Empfänger");
        long testId = id;

        //TODO testen ob die eingegene Id überhaupt existiert
        sendMessageService.send(testId,testEinzelanordnung,portNumber);

    }

    //WIRD NUR ZUM TESTEN VERWENDET
    //testet ob msb eine veränderte id erkennt
    @ShellMethod(key="fakeIdsend")
    public void sendMessageWithFakeId(long correctId, String portNumber){
        Einzelanordnung testEinzelanordnung = new Einzelanordnung("FakeIdPrüfer1","FakeIdPrüfer2",111111.00,"FakeIdEmpfänger");
        sendMessageService.sendFakeId(correctId,testEinzelanordnung,portNumber);
    }

    //WIRD NUR ZUM TESTEN VERWENDET
    //testet ob msb einen veränderten body erkennt
    @ShellMethod(key="fakeBodysend")
    public void sendMessageWithFakeBody(long correctId, String portNumber){
        Einzelanordnung testEinzelanordnung = new Einzelanordnung("FakebodyPrüfer1","FakebodyPrüfer2",111111.00,"FakebodyEmpfänger");
        sendMessageService.sendFakeBody(correctId, testEinzelanordnung, portNumber);
    }

    //DIENT NUR VORLÄUFIG DAZU RSA KEYPAIRS ZU GENERIEREN UM DAS KEYESTABLISHMENT TESTEN ZU KÖNNEN
    //TODO soll durch eine automatische Methode ersetzt werden, welche die Schlüssel im RSAService selber nach deren Ablauf ersetzt und die Schlüssel bei einer PKI anmeldet
    @ShellMethod(key="makeRSAKeyPair")
    public String generateKeyPair(int lengthInBits){

        KeyPairGenerator keyPairGenerator;
        try{
            keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        }catch (NoSuchAlgorithmException e){
            return "NO SUCH ALGORITHM";
        }

        keyPairGenerator.initialize(lengthInBits);

        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();
        return "public key: " + Base64.getEncoder().encodeToString(publicKey.getEncoded()) + "\n\nprivate key: " + Base64.getEncoder().encodeToString(privateKey.getEncoded());

    }

    //TESTET OB DIE BEIDEN EIGENEN SCHLÜSSEL ZUEINANDER PASSEN:
    //für 128 und 256 sicherheitsniveau
    @ShellMethod(key="testRSAkeyPairs")
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

    //SETZT NICHT DEN EIGENEN SESSION KEY!
    //TESTET OB DER SESSION KEY ORDENTLICH GENERIERT WIRD:
    @ShellMethod(key="gensessionkey")
    public String genSessionKey(String portNumber, int sicherheitsNiveau){
        //todo sicherheitsniveau kann entweder 256 oder 128 sein
        return Base64.getEncoder().encodeToString(rsAservice.generateSessionKey(portNumber, sicherheitsNiveau));
    }

    @Value("${msb.einzelanordnung.path}")
    private String msbEinzelanordnungPath;

    @ShellMethod(key="testGeneralSendToEinzelanordnungAPI")
    public void testGeneralSendToEinzelanordnungAPI(long id, String portNumber )throws Exception{
        Einzelanordnung testEinzelanordnung = new Einzelanordnung("Prüfer1","Prüfer2",9999.00,"Empfänger");
        String EinzalAnordnungJSON = objectMapper.writeValueAsString(testEinzelanordnung);

        byte[] encryptedEinzelanordnung = aeSservice.encrypt(EinzalAnordnungJSON,longToByte(id),portNumber);

        generalSendService.sendPut(encryptedEinzelanordnung,"http://" + msbHostIpAddress + ":" + portNumber + msbEinzelanordnungPath + "/" + id);
    }

    //TEST METHODE UM ZU SEHEN OB DER SESSIONKEY KORREKT GESETZT WIRD UND ZUM VER UND ENTSCHLÜSSELN IN AES VEWENDET WERDEN KANN
    @ShellMethod(key="genthenEncthenDec")
    public String testNewSessionKey(String portNumber, int sicherheitsniveau) throws Exception{
        rsAservice.generateNewAESconnection(portNumber, sicherheitsniveau);
        logger.info("neu gesetzter aes sessionkey als base64 String: " + Base64.getEncoder().encodeToString(aeSservice.getSessionKey(portNumber).getEncoded()));

        String plaintext = "hoffentlich funktioniert das";
        byte[] associatedData = "bitte bitte".getBytes();
        byte[] ciphertext = aeSservice.encrypt(plaintext,associatedData,portNumber);
        String decryptedCipherText = aeSservice.decrypt(ciphertext,associatedData,portNumber);

        if(decryptedCipherText.equals(plaintext)) return "Das Ver und Entschlüsseln mit dem durch RSA generierten AES session key funktioniert!";
        return "Etwas stimmt nicht mit dem neu erzeugten session key!";
    }

    @Value("${msb.host.ip.address}")
    private String msbHostIpAddress;

    @Value("${msb.test.path}")
    private String msbTestPath;
    private String generateMsbTestApi(String portNumber){
        return "http://" + msbHostIpAddress + ":" + portNumber + msbTestPath;
    }

    //FULL KEYESTABLISHMENT TEST
    //TEST in MSB matches the new one returne here
    @ShellMethod(key="testRSAfully")
    public String fullRSAtest(String portNumber, int sicherheitsniveau){
        String errorMessage = "Etwas ist schiefgegangen\nOld session key: " + Base64.getEncoder().encodeToString(aeSservice.getSessionKey(portNumber).getEncoded());

        rsAservice.generateNewAESconnection(portNumber,sicherheitsniveau);

        byte[] msbSessionKey  = generalSendService.sendTestGet(this.generateMsbTestApi(portNumber)).getBody();

        if(Arrays.equals(msbSessionKey,aeSservice.getSessionKey(portNumber).getEncoded())){
            return "alles passt: \nmsbSessionKey: " + Base64.getEncoder().encodeToString(msbSessionKey) +
                    "\nmsaSessionKey: " + Base64.getEncoder().encodeToString(aeSservice.getSessionKey(portNumber).getEncoded());
        }

        return errorMessage + "\n\nNew session key: " + Base64.getEncoder().encodeToString(aeSservice.getSessionKey(portNumber).getEncoded());
    }

    @ShellMethod(key="1-establish-session-key-test-session-key")
    public String establishSessioneyTestSessionkey(String portNumber, int sicherheitsniveau) throws Exception{

        //1.establish a new key with msb and set it
        rsAservice.generateNewAESconnection(portNumber,sicherheitsniveau);

        //2. get current sessionkey from msb
        byte[] msbSessionKey  = generalSendService.sendTestGet(this.generateMsbTestApi(portNumber)).getBody();

        //3. compare msbSessionkey and msaSessionkey
        byte[] msaSessionKey = this.aeSservice.getSessionKey(portNumber).getEncoded();
        if(!Arrays.equals(msbSessionKey,msaSessionKey)){
            return "msa and msb session keys do not match:\nmsaSessionkey: " + Base64.getEncoder().encodeToString(msaSessionKey) + "\nmsbSessionkey: " + Base64.getEncoder().encodeToString(msbSessionKey);
        }

        //4. test if the established AES sessionkey works:
        String plaintext = "please work";
        byte[] associatedData = "please".getBytes();
        byte[] cipherText = aeSservice.encrypt(plaintext,associatedData,portNumber);
        String decryptedCipher = aeSservice.decrypt(cipherText,associatedData,portNumber);

        if(!decryptedCipher.equals(plaintext)){
            return "msa and msb session keys do match, but the session key decryption/encryption does not work";
        }

        return("MSA and MSB session keys match and the new session key works: " + Base64.getEncoder().encodeToString(msaSessionKey));

    }

    @ShellMethod(key="2-establish-aes-connection-with-all-aes-Connections")
    public void getAlltheConnections(int sicherheitsniveau){
        rsAservice.establishAesConnectionsWithAllMsbInstances(sicherheitsniveau);
    }

    @ShellMethod(key="3-establish-aes-connection-with-all-msb-instances-and-send-loadbalanced-after-that")
    public void establishAesConnectionsThenSendLoadbalanced(int sicherheitsniveau){
        rsAservice.establishAesConnectionsWithAllMsbInstances(sicherheitsniveau);
        Einzelanordnung testEinzelanordnung = new Einzelanordnung("LOAD","BALANCED",9999.00,"Empfänger");
        for(long id = 0; id < 10; ++id){
            sendMessageService.sendLoadbalanced(id,testEinzelanordnung);
        }

    }


    //needs to be the same in msb
    private byte[] longToByte(long id){
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(id);
        return buffer.array();
    }

     */

    @ShellMethod(key = "send")
    public void send(int msgCnt){
        testService.generateAndSendEinzelanordnungen(msgCnt);
    }

    @ShellMethod(key="mysql-Connection-test")
    public void testMysqlConnection(){

        Einzelanordnung testEinzelanordnung = einzelanordnungGenerator.generateSignedEinzelanordnung(1111.0,"Empfänger",1,2);
        long idofsavedeinzelanordnung = testEinzelanordnung.getId();
        Einzelanordnung foundEinzelanordnung;
        try {
            foundEinzelanordnung = einzelanordnungRepositoryService.findById(idofsavedeinzelanordnung);
        } catch (ConnectException e) {
            logger.error("Could not execute findById on the id; "+ idofsavedeinzelanordnung);
            return;
        }
        logger.info("Saved and found einzelanordnung: " + foundEinzelanordnung.toString());
        logger.info("All found einzelanordnungen: ");
        List<Einzelanordnung> allSavedEinzelanordnungen = null;
        try {
            allSavedEinzelanordnungen = einzelanordnungRepositoryService.findAll();
        } catch (ConnectException e) {
            logger.error("Could not execute findall for einzelanordnungen properly");
        }
        for(Einzelanordnung e: allSavedEinzelanordnungen){
            logger.info(e.toString());
        }

        User testUser1 = new User("MC","lastName","coolpassword");
        User savedUser;
        try {
            savedUser = userRepositoryService.save(testUser1);
        } catch (ConnectException e) {
            logger.error("Error could not save user in DB2");
            return;
        }
        logger.info("Saved and found user: " + savedUser.toString());
        logger.info("All found users: ");
        List<User> allSavedUsers = null;
        try {
            allSavedUsers = userRepositoryService.findAll();
        } catch (ConnectException e) {
            logger.error("Could not execute find all users");
            return;
        }
        for(User u: allSavedUsers){
            logger.info(u.toString());
        }
    }

    @ShellMethod(key="generate-AES-Connection-then-send-saved-and-signed-anordnungen-loadbalanced")
    public void testMysqlAnordnungGeneration(int sicherheitsniveau, int messagesCnt){

        rsAservice.keyEstablishment(sicherheitsniveau);

        testService.generateAndSendEinzelanordnungen(messagesCnt);

    }

    @ShellMethod(key="time-syn-vs-async")
    public void testTime(int sicherheitsNiveau, int msgCnt)throws Exception{

        long startTimeSync = System.currentTimeMillis();

        rsAservice.keyEstablishment(sicherheitsNiveau);

        testService.generateAndSendEinzelanordnungen(msgCnt,"sync");

        long endTimeSync = System.currentTimeMillis();

        long startTimeAsync = System.currentTimeMillis();

        CompletableFuture<Void> asyncTask = CompletableFuture.runAsync(() -> {

            rsAservice.keyEstablishment(sicherheitsNiveau);

            testService.generateAndSendEinzelanordnungen(msgCnt, "async");

        });

        asyncTask.get();

        long endTimeAsync = System.currentTimeMillis();

        logger.info("----------Time for synchronous loadbalanced sending: " + (endTimeSync - startTimeSync) + " of " + msgCnt + " messages");
        logger.info("----------Time for asynchronous loadbalanced sending: " + (endTimeAsync - startTimeAsync) + " of " + msgCnt + " messages");

    }

    @ShellMethod(key="check-db2-initialization")
    public void testDB2init(){

        logger.info("All found users: ");
        List<User> allSavedUsers = null;
        try {
            allSavedUsers = userRepositoryService.findAll();
        } catch (ConnectException e) {
            logger.error("Could not execute findall users");
            return;
        }
        for(User u: allSavedUsers){
            logger.info(u.toString());
        }

    }

    @ShellMethod(key="active-ports")
    public void getActivePorts(){
        logger.info("Active ports: " + MsaMsbConnectionDetails.getActivePorts());
    }

    //DIENT NUR VORLÄUFIG DAZU RSA KEYPAIRS ZU GENERIEREN UM DAS KEYESTABLISHMENT TESTEN ZU KÖNNEN
    //TODO soll durch eine automatische Methode ersetzt werden, welche die Schlüssel im RSAService selber nach deren Ablauf ersetzt und die Schlüssel bei einer PKI anmeldet
    @ShellMethod(key="make100RSAKeyPairs")
    public String generate100KeyPair(int lengthInBits){

        KeyPairGenerator keyPairGenerator;
        try{
            keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        }catch (NoSuchAlgorithmException e){
            return "NO SUCH ALGORITHM";
        }

        keyPairGenerator.initialize(lengthInBits);

        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();
        return "public key: " + Base64.getEncoder().encodeToString(publicKey.getEncoded()) + "\n\nprivate key: " + Base64.getEncoder().encodeToString(privateKey.getEncoded());


    }


}
