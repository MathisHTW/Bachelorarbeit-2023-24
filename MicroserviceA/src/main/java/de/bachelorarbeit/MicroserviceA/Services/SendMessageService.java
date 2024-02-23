package de.bachelorarbeit.MicroserviceA.Services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bachelorarbeit.MicroserviceA.DB1.entities.Einzelanordnung;
import de.bachelorarbeit.MicroserviceA.Model.MsaMsbConnectionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.AEADBadTagException;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


@Service
public class SendMessageService {

    //private List<String> msbPorts = new ArrayList<>();
    private volatile int lastUsedPortIndex=0;

    //public void addMsbPort(String msbPort){
    //    logger.info("trying to add this port to msbports: " + msbPort);
    //    if(msbPorts.contains(msbPort)) return;
    //    msbPorts.add(msbPort);
    //    logger.info("added " + msbPort + " to msb ports");
    //}

    @Value("${msb.host.ip.address}")
    private String msbHostIpAddress;

    @Value("${msb.einzelanordnung.path}")
    private String msbEinzelanordnungPath;

    @Value("${known.msb.key.establishment.url}")
    private String msbBaseKeyEstablishmentUrl;

    @Value("${drop.rate}")
    private int dropChance;

    //wird für das Versenden der Nachricht benötigt
    //hat einen timeout von 250millisekunden
    RestTemplate restTemplate;

    //wird benötigt um das Objekt der einzelanordnung in eine JSON zu transformieren
    //(Diese JSON wird nach dem Entschlüsseln in MSB durch einen objectMapper wieder in
    //ein Einzelanordnungsobject umgewandelt)
    ObjectMapper objectMapper;

    //Wird für das Verschlüsseln des Requests msb benötigt und
    //Für das Entschlüsseln / Verifzieren der Response
    AESservice aeSservice;

    private RSAservice rsAservice;

    private EinzelanordnungRepositoryService einzelanordnungRepositoryService;

    private static Set<Long> unflaggedEinzelanordnung = new HashSet<>();

    private static Set<Long> notUpdatedEinzelanordnung = new HashSet<>();

    @Autowired
    SendMessageService(RestTemplate restTemplate, ObjectMapper objectMapper, AESservice aeSservice,EinzelanordnungRepositoryService einzelanordnungRepositoryService, RSAservice rsAservice){
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.aeSservice = aeSservice;
        this.einzelanordnungRepositoryService = einzelanordnungRepositoryService;
        this.rsAservice = rsAservice;
    }

    Logger logger = LoggerFactory.getLogger(SendMessageService.class);

    @Async
    public CompletableFuture<Void> sendLoadbalanced(long id){

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

            if (MsaMsbConnectionDetails.getActivePorts().isEmpty()) {
                logger.debug("ERROR no msbPorts known for aes loadbalanced sending");
                //The message will be sent to the active Ports as soon as they are available again
                //using the sendLoadbalancedRemaining method (see in InitRunner.java)
                return;
            }

            List<String> activePorts;
            int nextPortIndex;
            String nextPort;
            synchronized (this) {
                activePorts = MsaMsbConnectionDetails.getActivePorts();
                nextPortIndex = (lastUsedPortIndex + 1) % activePorts.size();
                lastUsedPortIndex = nextPortIndex;
                nextPort = activePorts.get(nextPortIndex);
            }

            logger.debug("Sending AES message to: " + nextPort);

            // USED FOR TESTING
            /*
            if (nextPort.equals("8081")) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("Sth went wrong with thread sleep");
                }
            }
             */


            this.send(id, nextPort);
        });

        return future;

    }

    /**
     * searches for all einzelanordnungen in the DB1 which were not yet successfully
     * received by msb and sends them again.
     */
    public void sendLoadbalancedRemaining(){
        logger.debug("Sending not yet received Einzelanordnungen to msb");

        LocalDateTime currentDateTime = LocalDateTime.now();

        List<Einzelanordnung> notYetReceivedByMSB = einzelanordnungRepositoryService.findAllNotReceived();

        if(notYetReceivedByMSB != null && !notYetReceivedByMSB.isEmpty()){
            List<Long> ids = new ArrayList<>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            //es werden nur Einzelanordnungen gesendet welche vor über 60 Sekunden generiert wurden
            for (Einzelanordnung einzelanordnung:notYetReceivedByMSB) {
                LocalDateTime anordnungGenerationTime = LocalDateTime.parse(einzelanordnung.getTimeStamp(), formatter);
                if(currentDateTime.isAfter(anordnungGenerationTime.plusSeconds(60))){
                    ids.add(einzelanordnung.getId());
                }

            }
            logger.info("Ids of Einzelanordnungen to be sent: " + ids);
            for (Long id: ids) {
                this.sendLoadbalanced(id);
            }
        }

    }

    //todo remove this is only for testing
    public void sendLoadbalancedSynchronous(long id) {

        List<String> activePorts = MsaMsbConnectionDetails.getActivePorts();

        activePorts.forEach(port -> logger.debug("inhalt von msbport: " + port));

        if(activePorts.size() == 0){
            logger.debug("ERROR no msbPorts known for aes loadbalanced sending");
            //throw new RuntimeException("msbports empty, loadbalanced sending is not possible");
            return;
        }

        int nextPortIndex = (lastUsedPortIndex + 1) % activePorts.size();

        String nextPort = activePorts.get(nextPortIndex);
        logger.debug("Sending AES message to: " + nextPort);

        this.send(id, nextPort);
        lastUsedPortIndex = nextPortIndex;
    }

        //TODO eigentlich wird keine einzelanordnung angegeben sondern nur die id
    //Die Methode holt sich anhand der id die Einzelanordnung aus der DB
    //Die Methode nutzt dann dieselbe ID welche von der DB für die Einzelanordung verwendet wurde
    //in dem completeMSBputPath, damit die Einzelanordnung in MSB unter derselben ID abgelegt wird
    public void send(long id, String portNumber){

        //todo wieder entfernen dient dem "testen"/anschauhen vom loadbalancing wenn mal ein port länger braucht
        //if(portNumber.equals("8081")){
        //    try {
        //        Thread.sleep(500);
        //    } catch (InterruptedException e) {
        //        throw new RuntimeException(e);
        //    }
        //}


        /*
        1. Einzelanordnung aus der DB holen mit hilfe des Id Parameters
        2. den Pfad zur msb Api mit der id definieren
        3. Einzelanordnung in einen JSON String umwandeln
        4. JSON String verschlüsseln mit AES-GCM, id = associated data
        5. Request mit verschlüsselter JSON und id an msb senden
        6. Response entschlüsseln
        7. ... Auf Response reagieren
         */

        //Einzelanordnung aus db holen
        Einzelanordnung einzelanordnung = null;
        try {
            einzelanordnung = einzelanordnungRepositoryService.findById(id);
        } catch (ConnectException e) {
            logger.debug("ERROR Anordnung with id: " + id + " could not be received from DB1");
            return;
        }
        if(einzelanordnung==null){
            logger.debug("ERROR Anordnung with id: " + id + " could not be found in DB1");
            return;
        }
        if(einzelanordnung.wasReceivedByMSB()){
            logger.debug("Anordnung with id: " + id + " was already received by MSB and will not be sent again");
            return;
        }

        logger.debug("Message Data: id = " + id + " - Einzelanordnung: " + einzelanordnung.toString());

        String completeMSBputPath = this.generateCompleteMSBputPath(portNumber,id);
        logger.debug("Einzelanordnung will be sent to: " + completeMSBputPath);

        //creating requestBody JSON String from einzelanordnung object
        String requestBody;
        try{
             requestBody= this.objectToJSON(einzelanordnung);
        }
        catch (JsonProcessingException exc){//sollte nicht passieren, denn dann stimmt etwas mit der implementierung nicht
            logger.error("ERROR sth went wrong turning einzelanordnung obj into JSON.");
            logger.error("Could not send: " + einzelanordnung + " with id: " + id);
            logger.info("Try sending the message with id " + id +  "again");
            this.sendLoadbalanced(id);
            return;
        }

        //logger.info("Turned einzelanordnung object into json string: " + requestBody);

        long timestamp = System.currentTimeMillis();
        requestBody = timestamp + ".separator678." + requestBody;

        //Abschluss der Erzeugung des Requestbodies
        //Verschlüssel die JSON Einzelanordnung mit der Id als associated Data, diese kann dann von MSB verifiziert werden
        byte[] encryptedRequestBody;
        byte[] additionalData = longToByte(id);
        try{
            encryptedRequestBody = aeSservice.encrypt(requestBody,additionalData,portNumber);
        }catch(Exception exc){//kann zum beispiel passieren, wenn die einzelanordnung schon aus der db geholt wurde aber zwischendurch die Connection auf inactive gesetzt wurde
            logger.error("Einzelanordnung with id " + id + " to port: " + portNumber + " could not be encrypted");
            logger.warn("MSB on port" + portNumber + " might have been flagged as inactive");
            logger.info("Try sending the message with id " + id +  "again");
            logger.debug("ERROR sth went wrong encrypting. requestBody as JSON: " + requestBody + " additional Data as byte array: " + new String(additionalData, StandardCharsets.UTF_8));
            this.sendLoadbalanced(id);
            return;
        }

        //setting http headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        //Erzeuge eine HTTP Entity aus dem verschlüsselten requestbody und den HTTP headern
        HttpEntity<byte[]> requestEntity = new HttpEntity<>(encryptedRequestBody, headers); //byte[] weil der encrypted Body vom Typ byte[]
        //logger.info("requestEntity fertiggestellt mit dem verschlüsseltem requestbody: " + new String(requestEntity.getBody(), StandardCharsets.UTF_8));

        //make put request and receive encrypted Response
        ResponseEntity<byte[]> enrcyptedResponse;
        long responseStatusCode;
        byte[] encryptedResponseBody;
        try{
            this.dropMessageForTesting(dropChance);
            enrcyptedResponse = restTemplate.exchange(completeMSBputPath, HttpMethod.PUT, requestEntity, byte[].class);
            responseStatusCode = enrcyptedResponse.getStatusCode().value();
            encryptedResponseBody = enrcyptedResponse.getBody();
        }catch (ResourceAccessException resexc){ //Wenn MSB nicht erreichbar ist wird die einzelanordnung erneut loadbalanced versendet
            logger.error("ERROR Connection to MSB on " + portNumber + " refused. For einzelanordnung with id: " + id);
            logger.info("message with id " + id + " will be resend");
            this.resend(portNumber,id);
            return;
        }
        catch (HttpStatusCodeException statusCodeException){ //Wenn kein 200er Code zurückkommt sondern z.b. 400
            responseStatusCode =statusCodeException.getStatusCode().value();
            encryptedResponseBody = statusCodeException.getResponseBodyAsByteArray();
        }

        //reset failure counter on the given Connection
        MsaMsbConnectionDetails.getActiveConnections().get(portNumber).resetLostMessagesCounter();

        //Wenn die Verschlüsselung der Response in msb fehlschlägt, dann existiert kein response body
        if(responseStatusCode == 500 && encryptedResponseBody.length == 0){
            logger.error("ERROR die Verschlüsselung der response nachricht in msb auf port: " + portNumber + " war fehlerhaft und dieser Statuscode damit nicht verifizierbar");
            logger.info("message with id " + id +" will be resend");
            this.resend(portNumber,id);
            return;
        }

        //logger.info("Response status Code (noch nicht verifiziert): " + responseStatusCode);
        //logger.info("Response body (noch nicht verifiziert): " + new String(encryptedResponseBody,StandardCharsets.UTF_8));

        //responsebody entschlüsseln und somit auch den Statuscode(als long als byte array) auf integrität prüfen
        String decryptedResponseBody;
        String[] decryptedTimestampAndBody;
        String receivedTimestamp;
        try {
            decryptedTimestampAndBody = aeSservice.decrypt(encryptedResponseBody,longToByte(responseStatusCode),portNumber).split("\\.separator678\\.");
            receivedTimestamp = decryptedTimestampAndBody[0];
            decryptedResponseBody = decryptedTimestampAndBody[1];
        }
        catch(AEADBadTagException tagExc){
            logger.error("ERROR der Authentifizierungs Tag der response message für id: " + id + " von port "+ portNumber + " konnte nicht verifiziert werden!");
            logger.info("message with id: " + id + " will be resend");
            this.resend(portNumber,id);
            return;
        }
        catch (Exception exc){
            logger.error("ERROR sth went wrong decrypting response message for einzelanordnung: " + id + " from port: " + portNumber + " Decrypt Methode überprüfen!");
            logger.info("message with id: " + id + " will be resend");
            this.resend(portNumber,id);
            return;
        }

        if(Long.parseLong(receivedTimestamp) != timestamp && !receivedTimestamp.equals("XXX")){
            logger.error("ERROR timestamp does not match");
            this.resend(portNumber,id);
            return;
        }

        //Mit errors umgehen
        if(responseStatusCode != 200){
            logger.error("ERROR received status Code " + responseStatusCode + " of id: "+ id +" from MSB and this message: " + decryptedResponseBody);

            if(responseStatusCode == 500){
                switch (decryptedResponseBody){
                    case "ERROR-DB1-MSB":
                        logger.warn("Cancel sending msg with id: " + id + " because of error in DB1 at MSB");
                        return;
                    case "ERROR-DB2-MSB":
                        logger.warn("Cancel sending msg with id: " + id + " because of error in DB2 at MSB");
                        return;
                    case "ERROR-MESSAGE-COULD-NOT-BE-DECRYPTED-MSB": //sollte eigentlich nur passieren, wenn etwas mit der nachricht passiert ist, und eigentlich nichtmal dann wegen GCM
                        logger.warn("Message with id: " + id + " could not be decrypted by MSB on port: " + portNumber);
                        break;
                    case "ERROR-TURN-JSON-INTO-OBJEKT-MSB"://sollte eigentlich nur passieren, wenn etwas mit der nachricht passiert ist, und eigentlich nichtmal dann wegen GCM
                        logger.warn("JSON of Message with id: " + id + " could not be turned into a object by MSB on port: " + portNumber);
                        break;
                    case "ERROR-VERIFYING-PROCESS-MSB"://sollte eigentlich nur passieren, wenn etwas mit der nachricht passiert ist, und eigentlich nichtmal dann wegen GCM
                        logger.warn("Signatures of message with id: " + id + " could not be verified, because of the verifying process in MSB on port: " + portNumber);
                        break;
                }
            }else if(responseStatusCode == 400){
                switch (decryptedResponseBody){
                    case "ERROR-GCM-AUTHENTICATION-TAG-COULD-NOT-BE-VERIFIED-MSB":
                        logger.warn("Authentication Tag of message with id: " +  id + " could not be verified by MSB on port: " + portNumber);
                        break;
                    case "ERROR-SIGNATURES-COULD-NOT-BE-VERIFIED":
                        logger.error("Signatures for message with id: " + id + " could not be verified by MSB on port: " + portNumber);
                        logger.error("THIS INCIDENT WILL BE REPORTED");
                        boolean wasflagged = einzelanordnungRepositoryService.flag(id);
                        if(!wasflagged){
                            unflaggedEinzelanordnung.add(id);
                        }
                        return;
                }
            }

            logger.info("message with id: " + id + " will be resend");
            this.resend(portNumber,id);
            return;
        }

        //mit 200er umgehen
        logger.debug("SUCCESS received status Code from MSB = " + responseStatusCode + " and this message(decrypted): " + decryptedResponseBody);

        long idOfReceivedAnordnung = Long.parseLong(decryptedResponseBody);
        if(idOfReceivedAnordnung == -1){
            logger.warn("WARN id " + id + " is already known in MSB DB.");
            idOfReceivedAnordnung = id;
        }
        Einzelanordnung savedUpdatedEinzelanordnung = einzelanordnungRepositoryService.updateReceivedStatus(idOfReceivedAnordnung);
        if(savedUpdatedEinzelanordnung == null){
            logger.debug("ERROR could not update received status of message with id: " + id);
            notUpdatedEinzelanordnung.add(id);
        }
        logger.debug("SUCCESS the status was updated in the DB1: " + savedUpdatedEinzelanordnung.toString());

    }

    /**
     * Testing method that drops messages with a chance of dropChance %
     * @param dropChance
     */
    private void dropMessageForTesting(int dropChance) throws ResourceAccessException{
        Random random = new Random();
        int randomNumber = random.nextInt(100);
        if(randomNumber <= dropChance){
            try {
                Thread.sleep(350);//wait timeout for resttemplate
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            logger.warn("Message was dropped");
            throw new ResourceAccessException("Message was dropped");
        }
    }

    private void resend(String portNumber,long id){
        synchronized (this) {
            MsaMsbConnectionDetails connectionDetails = MsaMsbConnectionDetails.getActiveConnection(portNumber);
            if (connectionDetails != null) {//kann passieren wenn eine anderer Thread die connection schon auf inactive gesetzt hat
                connectionDetails.incLostMessagesCnt();
                if (!connectionDetails.isActive()) {
                    //aeSservice.removeBrokenConnection(portNumber);
                    //this.msbPorts.remove(portNumber);
                    logger.error("ERROR connection to port " + portNumber + " is flagged INACTIVE.");
                    logger.error("INFO Starting keyestablishment with port: " + portNumber);
                    rsAservice.keyEstablishment(portNumber, connectionDetails.getSecurityLevel());
                }
            }
        }
        CompletableFuture<Void> future = this.sendLoadbalanced(id);
        try {
            future.get();
        }catch (InterruptedException | ExecutionException e) {
            logger.error("resend was interrupted");
        }
    }

    private String generateCompleteMSBputPath(String portNumber, long id){
        return "http://" + msbHostIpAddress + ":" + portNumber + msbEinzelanordnungPath + "/" + id;
    }

    private String objectToJSON(Object obj) throws JsonProcessingException{
        return objectMapper.writeValueAsString(obj);
    }

    //needs to be the same in msb
    private byte[] longToByte(long id){
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(id);
        return buffer.array();
    }

    private static long byteToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return buffer.getLong();
    }

    //generiert den Verschlüsselten Text und Tag mit correctId
    //sendet aber an /msb/einzelanordnung/fakeid
    public void sendFakeId(long correctId, Einzelanordnung einzelanordnung, String portNumber){

        long fakeId = correctId + 1;

        //logger.info("Prepare to send message to MSB");
        //TODO Einzelanordnung aus der DB holen, abbruch wenn keine anordnung gefunden
        logger.info("Message Data: id = " + correctId + " - Einzelanordnung: " + einzelanordnung.toString());

        String completeMSBputPath = this.generateCompleteMSBputPath(portNumber,fakeId);

        logger.info("Das Ziel für den Put Request ist:  " + completeMSBputPath);

        //creating requestBody JSON String from einzelanordnung object
        String requestBody;
        try{
            requestBody= this.objectToJSON(einzelanordnung);
        }
        catch (JsonProcessingException exc){
            logger.error("ERROR sth went wrong turning einzelanordnung obj into JSON.");
            logger.error("Could not send: " + einzelanordnung + " with id: " + correctId);
            return;
            //ToDo try sending message again?
        }

        //logger.info("Turned einzelanordnung object into json string: " + requestBody);

        //Abschluss der Erzeugung des Requestbodies
        //Verschlüssel die JSON Einzelanordnung mit der Id als associated Data, diese kann dann von MSB verifiziert werden
        byte[] encryptedRequestBody;
        byte[] additionalData = longToByte(correctId);
        try{
            encryptedRequestBody = aeSservice.encrypt(requestBody,additionalData, portNumber);
        }catch(Exception exc){
            logger.error("ERROR sth went wrong encrypting. requestBody as JSON: " + requestBody + " additional Data as byte array: " + new String(additionalData, StandardCharsets.UTF_8));
            return;
            //todo (encrypt method überprüfen und) erneut senden
        }

        //setting http headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        //Erzeuge eine HTTP Entity aus dem verschlüsselten requestbody und den HTTP headern
        HttpEntity<byte[]> requestEntity = new HttpEntity<>(encryptedRequestBody, headers); //byte[] weil der encrypted Body vom Typ byte[]
        //logger.info("requestEntity fertiggestellt mit dem verschlüsseltem requestbody: " + new String(requestEntity.getBody(), StandardCharsets.UTF_8));

        //TODO muss eventuell von String auf byte wenn responseBody verschlüsselt ist
        //TODO muss entschlüsseln wenn Response verschlüsselt ist
        //TODO also minimal muss der Statuscode verfifiziert von MSB kommen!
        //make put request and receive Response
        ResponseEntity<byte[]> enrcyptedResponse;
        long responseStatusCode;
        byte[] encryptedResponseBody;
        try{
            enrcyptedResponse = restTemplate.exchange(completeMSBputPath, HttpMethod.PUT, requestEntity, byte[].class);
            responseStatusCode = enrcyptedResponse.getStatusCode().value();
            encryptedResponseBody = enrcyptedResponse.getBody();
        }catch (ResourceAccessException resexc){ //Wenn MSB nicht erreichbar ist wird abgebrochen
            logger.error("ERROR Connection refused. Keine Verbindung zu MSB. Senden wird abgebrochen.");
            //resexc.printStackTrace();
            return;
            //Todo erneut senden versuchen
        }
        catch (HttpStatusCodeException statusCodeException){
            responseStatusCode =statusCodeException.getStatusCode().value();
            encryptedResponseBody = statusCodeException.getResponseBodyAsByteArray();
        }

        //pull statuscode und encryptedBody aus der encryptedResponse
        //long responseStatusCode = enrcyptedResponse.getStatusCode().value();
        //byte[] encryptedResponseBody = enrcyptedResponse.getBody();

        //Wenn die Verschlüsselung der Response in msb fehlschlägt, dann existiert kein response body
        if(responseStatusCode == 500 && encryptedResponseBody.length == 0){
            logger.error("ERROR die Verschlüsselung der response nachricht in msb wahr fehlerhaft und dieser Statuscode damit nicht verifizierbar");
            return;
            //Todo erneut senden
        }

        //logger.info("Response status Code (noch nicht verifiziert): " + responseStatusCode);
        //logger.info("Response body (noch nicht verifiziert): " + new String(encryptedResponseBody,StandardCharsets.UTF_8));

        //responsebody entschlüsseln und somit auch den Statuscode(als long als byte array) auf integrität prüfen
        String decryptedResponseBody;
        try {
            decryptedResponseBody = aeSservice.decrypt(encryptedResponseBody,longToByte(responseStatusCode),portNumber);
        }
        catch(AEADBadTagException tagExc){//kann passieren wenn ein msb restarted aber noch keinen neuen sessionkey mit msa ausgehandelt hat
            logger.error("ERROR der Authentifizierungs Tag der response message konnte nicht verifiziert werden!");
            return;
            //todo nachricht erneut senden
        }
        catch (Exception exc){
            logger.error("ERROR sth went wrong decrypting response message. Decrypt Methode überprüfen!");
            return;
            //TODO Nachricht erneut senden
        }

        if(responseStatusCode != 200){
            logger.error("ERROR received status Code " + responseStatusCode + " from MSB and this message: " + decryptedResponseBody);
            return;
            //todo nachricht erneut senden
        }

        logger.info("SUCCESS received status Code from MSB = " + responseStatusCode + " and this message (decrypted): " + decryptedResponseBody);

        //ToDo Wenn ein 200er code zurückkommt, dann kann die einzelanordnung in der eigenen DB als erledigt abgehakt werden

    }

    public void sendFakeBody(long id, Einzelanordnung einzelanordnung, String portNumber){

        //logger.info("Prepare to send message to MSB");
        //TODO Einzelanordnung aus der DB holen, abbruch wenn keine anordnung gefunden
        logger.info("Message Data: id = " + id + " - Einzelanordnung: " + einzelanordnung.toString());

        String completeMSBputPath = this.generateCompleteMSBputPath(portNumber,id);

        logger.info("Das Ziel für den Put Request ist:  " + completeMSBputPath);

        //creating requestBody JSON String from einzelanordnung object
        String requestBody;
        try{
            requestBody= this.objectToJSON(einzelanordnung);
        }
        catch (JsonProcessingException exc){
            logger.error("ERROR sth went wrong turning einzelanordnung obj into JSON.");
            logger.error("Could not send: " + einzelanordnung + " with id: " + id);
            return;
            //ToDo try sending message again?
        }

        //logger.info("Turned einzelanordnung object into json string: " + requestBody);

        //Abschluss der Erzeugung des Requestbodies
        //Verschlüssel die JSON Einzelanordnung mit der Id als associated Data, diese kann dann von MSB verifiziert werden
        byte[] encryptedRequestBody;
        byte[] additionalData = longToByte(id);
        try{
            encryptedRequestBody = aeSservice.encrypt(requestBody,additionalData,portNumber);
        }catch(Exception exc){
            logger.error("ERROR sth went wrong encrypting. requestBody as JSON: " + requestBody + " additional Data as byte array: " + new String(additionalData, StandardCharsets.UTF_8));
            return;
            //todo (encrypt method überprüfen und) erneut senden
        }

        //Requestbody abändern:
        Random random = new Random();
        int bitPositionToFlip = random.nextInt(encryptedRequestBody.length);
        int byteIndex = bitPositionToFlip / 8;
        int bitIndex = bitPositionToFlip % 8;
        byte mask = (byte) (1 << bitIndex);
        encryptedRequestBody[byteIndex] ^= mask;

        //logger.info("Fake Body= " + new String (encryptedRequestBody,StandardCharsets.UTF_8));

        //setting http headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        //Erzeuge eine HTTP Entity aus dem verschlüsselten requestbody und den HTTP headern
        HttpEntity<byte[]> requestEntity = new HttpEntity<>(encryptedRequestBody, headers); //byte[] weil der encrypted Body vom Typ byte[]
        //logger.info("requestEntity fertiggestellt mit dem verschlüsseltem requestbody: " + new String(requestEntity.getBody(), StandardCharsets.UTF_8));

        //TODO muss eventuell von String auf byte wenn responseBody verschlüsselt ist
        //TODO muss entschlüsseln wenn Response verschlüsselt ist
        //TODO also minimal muss der Statuscode verfifiziert von MSB kommen!
        //make put request and receive Response
        ResponseEntity<byte[]> enrcyptedResponse;
        long responseStatusCode;
        byte[] encryptedResponseBody;
        try{
            enrcyptedResponse = restTemplate.exchange(completeMSBputPath, HttpMethod.PUT, requestEntity, byte[].class);
            responseStatusCode = enrcyptedResponse.getStatusCode().value();
            encryptedResponseBody = enrcyptedResponse.getBody();
        }catch (ResourceAccessException resexc){ //Wenn MSB nicht erreichbar ist wird abgebrochen
            logger.error("ERROR Connection refused. Keine Verbindung zu MSB. Senden wird abgebrochen.");
            //resexc.printStackTrace();
            return;
            //Todo erneut senden versuchen
        }
        catch (HttpStatusCodeException statusCodeException){
            responseStatusCode =statusCodeException.getStatusCode().value();
            encryptedResponseBody = statusCodeException.getResponseBodyAsByteArray();
        }

        //pull statuscode und encryptedBody aus der encryptedResponse
        //long responseStatusCode = enrcyptedResponse.getStatusCode().value();
        //byte[] encryptedResponseBody = enrcyptedResponse.getBody();

        //Wenn die Verschlüsselung der Response in msb fehlschlägt, dann existiert kein response body
        if(responseStatusCode == 500 && encryptedResponseBody.length == 0){
            logger.error("ERROR die Verschlüsselung der response nachricht in msb wahr fehlerhaft und dieser Statuscode damit nicht verifizierbar");
            return;
            //Todo erneut senden
        }

        //logger.info("Response status Code (noch nicht verifiziert): " + responseStatusCode);
        //logger.info("Response body (noch nicht verifiziert): " + new String(encryptedResponseBody,StandardCharsets.UTF_8));

        //responsebody entschlüsseln und somit auch den Statuscode(als long als byte array) auf integrität prüfen
        String decryptedResponseBody;
        try {
            decryptedResponseBody = aeSservice.decrypt(encryptedResponseBody,longToByte(responseStatusCode),portNumber);
        }
        catch(AEADBadTagException tagExc){
            logger.error("ERROR der Authentifizierungs Tag der response message konnte nicht verifiziert werden!");
            return;
            //todo nachricht erneut senden
        }
        catch (Exception exc){
            logger.error("ERROR sth went wrong decrypting response message. Decrypt Methode überprüfen!");
            return;
            //TODO Nachricht erneut senden
        }

        if(responseStatusCode != 200){
            logger.error("ERROR received status Code" + responseStatusCode + " from MSB and this message: " + decryptedResponseBody);
            return;
            //todo nachricht erneut senden
        }

        logger.info("SUCCESS received status Code from MSB = " + responseStatusCode + " and this message (decrypted): " + decryptedResponseBody);

        //ToDo Wenn ein 200er code zurückkommt, dann kann die einzelanordnung in der eigenen DB als erledigt abgehakt werden

    }

    public void flagUnflagged() {
        boolean wasFlagged;
        for(long id: unflaggedEinzelanordnung){
           wasFlagged = einzelanordnungRepositoryService.flag(id);
           if(wasFlagged) unflaggedEinzelanordnung.remove(id);
        }
    }

    public void updateNotUpdated(){
        Einzelanordnung updatedEinzelanordnung;
        for(long id: notUpdatedEinzelanordnung){
            updatedEinzelanordnung = einzelanordnungRepositoryService.updateReceivedStatus(id);
            if(updatedEinzelanordnung != null) notUpdatedEinzelanordnung.remove(id);
        }
    }
}
