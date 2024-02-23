package de.bachelorarbeit.MicroserviceB.REST;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bachelorarbeit.MicroserviceB.DB1.entities.Einzelanordnung;
import de.bachelorarbeit.MicroserviceB.DB2.entities.User;
import de.bachelorarbeit.MicroserviceB.Services.AESservice;
import de.bachelorarbeit.MicroserviceB.Services.EinzelanordnungRepositoryService;
import de.bachelorarbeit.MicroserviceB.Services.UserRepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.crypto.AEADBadTagException;


import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Arrays;

@RestController
@RequestMapping("${einzelanordnung.basic.path}")
public class MSBRestController {

    //wird für das Entschlüsseln der Nachrichten und das Verschlüsseln der Responses verwendet
    AESservice aeSservice;

    //wird dafür verwendet die enschlüsselte JSON in ein Einzelanordnungsobjekt Objekt umzuwandeln
    ObjectMapper objectMapper;

    EinzelanordnungRepositoryService einzelanordnungRepositoryService;

    UserRepositoryService userRepositoryService;

    @Value("${msg.delay}")
    int msgDelay;

    @Autowired
    public MSBRestController(AESservice aeSservice, ObjectMapper objectMapper, EinzelanordnungRepositoryService einzelanordnungRepositoryService, UserRepositoryService userRepositoryService) {
        this.aeSservice = aeSservice;
        this.objectMapper = objectMapper;
        this.einzelanordnungRepositoryService = einzelanordnungRepositoryService;
        this.userRepositoryService = userRepositoryService;
    }

    Logger logger = LoggerFactory.getLogger(MSBRestController.class);

    /**
     * @param id unter dem die Einzelanordnung in der DB gespeichert werden soll
     * @param cipherMessage verschlüsselte JSON Darstellung des Einzelanordnungsobjekts. Ist ein byte array.
     * @return eine ResponseEntity die eine verschlüsselte String Nachricht enthält. Der StatusCode wird als asociated data in der AES-GCM Verschlüsselung genutzt
     */
    @PutMapping("{id}")
    public ResponseEntity<byte[]> save(@PathVariable long id, @RequestBody byte[] cipherMessage){

        //dient der simulation der Übertragunszeit zwischen msa und msb
        try {
            Thread.sleep(msgDelay);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        logger.info("Received Request for Id: " + id);
        //logger.info("Put request erhalten. Id: " + id + " - RequestBody(verschlüsselte JSON): " + new String(cipherMessage));

        //verschlüsselte einzelanordnung JSON entschlüsseln:



        String decryptedJSON;
        String timestamp;
        String[] timestampAndJSON;
        try{
            timestampAndJSON = aeSservice.decrypt(cipherMessage, longToByte(id)).split("\\.separator678\\.");
            timestamp = timestampAndJSON[0];
            decryptedJSON = timestampAndJSON[1]; //(die id des put paths ist die associated data welche auf integrität geprüft werden muss)
        }
        catch(AEADBadTagException exc){
            logger.error("ERROR message konnte nicht authentifiziert werden");
            ResponseEntity<byte[]> encryptedResponse = generateEncryptedResponse(400,"ERROR-GCM-AUTHENTICATION-TAG-COULD-NOT-BE-VERIFIED-MSB","XXX");
            //logger.info("Encrypted Response. Statuscode = " + encryptedResponse.getStatusCode().value() + " requestbody = " + new String(encryptedResponse.getBody(), StandardCharsets.UTF_8));
            return encryptedResponse;
        }
        catch (Exception exc){
            logger.error("ERROR sth went wrong decrypting the message");
            return generateEncryptedResponse(500,"ERROR-MESSAGE-COULD-NOT-BE-DECRYPTED-MSB","XXX");
        }

        //logger.info("Decrypted JSON: " + decryptedJSON);

        //entschlüsselte JSON in ein einzelanordnungs objekt umwandeln
        Einzelanordnung einzelanordnung;
        try {
            einzelanordnung = objectMapper.readValue(decryptedJSON,Einzelanordnung.class);
        } catch (JsonProcessingException e) {
            logger.error("ERROR sth went wrong turning the decrypted json into Einzelanordnung object");
            return generateEncryptedResponse(500,"ERROR-TURN-JSON-INTO-OBJEKT-MSB",timestamp);
        }

        //logger.info("Das aus der entschlüsselten JSON erzeugte einzelanordnung objekt: " + einzelanordnung);


        //TODO
        //  Das die unterschriebenen Daten aus dem einzelanordnungsobjekt herausholen
        //  die Unterschrift1 mit dem public key des pruefers1 entschlüsseln
        //  die Unterschrift2 mit dem public key des pruefers2 entschlüsseln
        //  die entschlüsselte Unterschrift1 selber erzeugen mit hash funktion
        //  die entschlüsselte Unterschrift2 selber erzeugen mit hash funktion
        //  die erhaltene entschlüsselte Unterschrift1 mit der erzeugten Unterschrift1 vergleichen
        //  die erhaltene entschlüsselte Unterschrift2 mit der erzeugten Unterschrift2 vergleichen
        //  Abbruch wenn die nicht übereinstimmen

        //Testweises überprüfen der unterschrift:
        /*int idPruefer1 = einzelanordnung.getPruefer1Id();
        int idPruefer2 = einzelanordnung.getPruefer2Id();
        String pruefer1FirstName = userRepositoryService.findById(idPruefer1).getFirstName();
        String pruefer2FirstName = userRepositoryService.findById(idPruefer2).getFirstName();
        if(!(Arrays.equals(pruefer1FirstName.getBytes(),einzelanordnung.getUnterschrift1()) && Arrays.equals(pruefer2FirstName.getBytes(),einzelanordnung.getUnterschrift2()))){
            logger.error("ERROR Unterschriften passen nicht zur einzelanordnung. Anordnung wird nicht in der DB gespeichert");
            return generateEncryptedResponse(400,"Unterschriften passen nicht!");
        }
         */

        //logger.info("Received Einzelanordnung decrypted: " + einzelanordnung);

        boolean signaturesAreVerified;
        try{
            signaturesAreVerified = this.signaturesAreVerified(einzelanordnung);
        }catch (ConnectException exc){
            logger.error("ERROR Sth went wrong verifying the signatures, could not Access DB2");
            return generateEncryptedResponse(500,"ERROR-DB2-MSB",timestamp);
        }catch (SignatureException exc){
            logger.error("ERROR Sth went wrong verifying the signatures, because of the Verifying process");
            return generateEncryptedResponse(500,"ERROR-VERIFYING-PROCESS-MSB",timestamp);
        }

        if(!signaturesAreVerified){
            logger.error("ERROR Unterschriften passen nicht zur einzelanordnung oder ids konnten nich in DB2 gefunden werden. Anordnung wird nicht in der DB gespeichert");
            return generateEncryptedResponse(400,"ERROR-SIGNATURES-COULD-NOT-BE-VERIFIED",timestamp);
        }

        //wenn die id schon in der DB existiert, wird die Anordnung nicht abgespeichert und es wird die id -1 zurückgeschickt
        Einzelanordnung savedEinzelanordnung;
        try{
            savedEinzelanordnung = einzelanordnungRepositoryService.save(einzelanordnung);
        }catch (ConnectException exc){
            logger.error("Could not save decrypted and verified einzelanordnung in DB1");
            return generateEncryptedResponse(500,"ERROR-DB1-MSB",timestamp);
        }
        if(savedEinzelanordnung == null){//there is already a entry in DB1 under the same id
            return generateEncryptedResponse(200,"-1",timestamp);
        }

        logger.info("SUCCESS saved Einzelanordnung with id: " + savedEinzelanordnung.getId() + " in DB1");

        //sende die Id der gespeicherten einzelanordnung zurück
        return generateEncryptedResponse(200,String.valueOf(savedEinzelanordnung.getId()),timestamp);

    }

    //needs to be the same in msa
    //die id aus dem put path muss in ein byte array umgewandelt werden um von der
    //decrypt methode als associatied data auf integrität geprüft werden zu können
    private byte[] longToByte(long id){
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(id);
        return buffer.array();
    }

    /**
     *
     * @param statusCode der Responsemessage
     * @param responseMessage inhalt der Responsemessage
     * @return ResponseEntity mit Verschlüsseltem body und verifiziertem Statuscode durch aes-gcm
     * Ausnahme: wenn die Verschlüsselung fehlschlägt wird nur ein 500 Statuscode versendet ohne responsebodyInhalt
     * Wenn ein Statuscode verwendet wird der noch nicht bekannt ist oder 500 dann wird Internal_server_Error zurückgeschickt
     */
    private ResponseEntity<byte[]> generateEncryptedResponse(long statusCode, String responseMessage, String timestamp){
        //verschlüssele die responseMessage
        //nutze den Statuscode als associated data damit dieser verifiziert werden kann
        byte[] statusCodeArray = longToByte(statusCode);
        byte[] encryptedRequestBody;
        try {
            encryptedRequestBody = aeSservice.encrypt(timestamp + ".separator678." + responseMessage,statusCodeArray);
        }catch (Exception exc){
            logger.error("ERROR sth went wrong encrypting the response message");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new byte[0]);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");

        logger.debug("response body status code: " + statusCode);
        logger.debug("plaintext response body: " + responseMessage);
        logger.debug("Encrypted responsebody lenght: " + encryptedRequestBody.length);

        switch ((int) statusCode){
            case 200:  return new ResponseEntity<byte[]>(encryptedRequestBody, headers, HttpStatus.OK);
            case 400: return new ResponseEntity<byte[]>(encryptedRequestBody, headers, HttpStatus.BAD_REQUEST);
            //case 500: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(encryptedRequestBody);
            default: return new ResponseEntity<byte[]>(encryptedRequestBody, headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    /**
     * verifiziert die unterschriften der entschlüsselten Einzalanordnung
     * @param einzelanordnung
     * @return true wenn die Unterschriften stimmen, false wenn nicht z.B. wegen falscher Unterschrift oder wenn die id eines prüfers nicht in DB2 existiert
     * @throws ConnectException wenn die DB2 nich erreichbar ist
     * @throws SignatureException wenn etwas mit dem Prozess Verifikation der Unterschriften nicht gestimmt hat
     */
    private boolean signaturesAreVerified(Einzelanordnung einzelanordnung)throws ConnectException, SignatureException {

        //1 generate raw data byte array
        double betrag = einzelanordnung.getBetrag();
        String empfaenger = einzelanordnung.getEmpfaenger();
        String timeStamp = einzelanordnung.getTimeStamp();
        int pruefer1id = einzelanordnung.getPruefer1Id();
        int pruefer2id = einzelanordnung.getPruefer2Id();
        byte[] unterschrift1 = einzelanordnung.getUnterschrift1();
        byte[] unterschrift2 = einzelanordnung.getUnterschrift2();
        byte[] payload = einzelanordnung.getPayload();

        User pruefer1;
        User pruefer2;

        //throw connectException
        pruefer1 = userRepositoryService.findById(pruefer1id);
        pruefer2 = userRepositoryService.findById(pruefer2id);


        if (pruefer1 == null) {
            logger.error("ERROR user could not be found in DB2 for given id: " + pruefer1id);
            logger.error("Verification of einzelanordnung canceled");
            return false;
        }
        if (pruefer2 == null) {
            logger.error("ERROR user could not be found in DB2 for given id: " + pruefer2id);
            logger.error("Verification of einzelanordnung canceled");
            return false;
        }

        //todo runtimeexception in exception ändern in verify methode und dann in dem endpoint behandeln
        boolean signature1Isverified = pruefer1.verify(betrag,empfaenger,timeStamp, payload, pruefer1id,unterschrift1);
        boolean signature2Isverified = pruefer2.verify(betrag,empfaenger,timeStamp, payload, pruefer2id,unterschrift2);

        return signature1Isverified && signature2Isverified;

    }



}
