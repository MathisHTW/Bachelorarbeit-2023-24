package de.bachelorarbeit.MicroserviceB.REST;

import de.bachelorarbeit.MicroserviceB.Services.AESservice;
import de.bachelorarbeit.MicroserviceB.Services.RSAservice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64;

@Controller
@RequestMapping("${keyestablishment.basic.path}")
public class KeyEstablishmentController {

    AESservice aeSservice;

    RSAservice rsAservice;

    @Autowired
    public KeyEstablishmentController(RSAservice rsAservice, AESservice aeSservice) {
        this.rsAservice = rsAservice;
        this.aeSservice = aeSservice;
    }

    @Value("${msb.port.range}")
    String msbPortRange;

    Logger logger = LoggerFactory.getLogger(KeyEstablishmentController.class);

    @PutMapping("/Cu")
    public ResponseEntity<byte[]> receiveCuSendCv(@RequestBody byte[] Cu){

        //Cu entschlüsseln mit eigenem privaten key zu Zu, und den msa key für die Verschlüsselung von Zv auswählen
        //Wenn cu = 384 byte = 3072bits groß, dann weiß ich, dass Sicherheitsniveau 128 genutzt wird
        //Wenn cu = 1920 byte = 15360bits groß, dann weiß ich, dass Sicherheitsniveau 256 genutzt wird

        int keyLength = Cu.length * 8;
        int sicherheitsNiveau = rsAservice.getSecurityLevel(keyLength);
        if(!rsAservice.securityLevelIsValid(sicherheitsNiveau)){
            logger.error("ERROR Security level of Cu is not supported. Security Level of Cu: " + sicherheitsNiveau);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new byte[0]);
        }

        PublicKey msaPubKey = rsAservice.getMsaPublicKey(sicherheitsNiveau);
        if(msaPubKey == null){
            logger.error("Error no msa public key found for the security level: " + sicherheitsNiveau);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new byte[0]);
        }

        byte[] Zu;
        try {
            Zu = rsAservice.decrypt(Cu,sicherheitsNiveau);
        }catch (IllegalArgumentException exc){
            logger.error("ERROR RSA decryption method does not accept the security level: " + sicherheitsNiveau);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new byte[0]);
        }
        if(Zu == null){
            logger.error("ERROR etwas ist beim Entschlüsseln von Cu schiefgegangen.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new byte[0]);
        }

        //logger.info("Cu erhalten und in Zu entschlüsselt: ");
        //logger.info("Erhaltene Zu als base64 String: " + Base64.getEncoder().encodeToString(Zu));

        //Zv generieren
        byte[] Zv;
        try {
            Zv = rsAservice.generateZv(keyLength);
        }catch (IllegalArgumentException exc){
            logger.error("Error keylength " + keyLength + " is not supported for generating Zv");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new byte[0]);
        }
        //logger.info("Erzeugte Zv als base64 String: " + Base64.getEncoder().encodeToString(Zv));

        //Zv mit msaPublickey verschlüsseln und so Cv generieren
        byte[] Cv = rsAservice.encrypt(Zv,msaPubKey);
        if(Cv == null){
            logger.error("ERROR sth went wrong encrypting Zv");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new byte[0]);
        }
        //logger.info("erzeugtes Cv: " + Base64.getEncoder().encodeToString(Cv));

        //gemeinsames Geheimnis aus Zu und Zv zusammensetzen
        byte[] Z = new byte[Zu.length + Zv.length];
        //Dies muss in msb genauso sein!
        System.arraycopy(Zu, 0, Z, 0, Zu.length);
        System.arraycopy(Zv, 0, Z, Zu.length, Zv.length);

        //logger.info("Erzeugtes gemeinsames Geheimnis Z als base64 String: " + Base64.getEncoder().encodeToString(Z));

        //aus Z einen gemeinsamen Schlüssel erzeugen:
        //1. Fixed Info herstellen
        byte[] fixedInfo = rsAservice.generateFixedInfo(sicherheitsNiveau); //todo fixedinfo in der singleStepKDF generieren
        if(fixedInfo == null) {
            logger.error("Error fixedInfo could not be generated because of unsuported security level");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new byte[0]);
        }

        //2. derived Keying material(Mackey + Sessionkey) aus Z und fixedInfo generieren
        byte[] derivedKeyingMaterial;
        try {
            derivedKeyingMaterial = rsAservice.singleStepKDF(Z,fixedInfo,sicherheitsNiveau);
        }catch (NoSuchAlgorithmException e){
            logger.error("Error der Sha Algorithmus für die Erzeugung des derived Keying Materials konnte nicht gefunden werden. KeyEstablishment wird abgebrochen.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new byte[0]);
        }

        if(derivedKeyingMaterial == null){
            logger.error("ERROR security level is not supported by KDF function");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new byte[0]);
        }

        //logger.info("-_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_-");
        //logger.info("Z: " + Base64.getEncoder().encodeToString(Z));
        //logger.info("fixedInfo: " + Base64.getEncoder().encodeToString(fixedInfo));
        //logger.info("sicherheitsniveau: " + sicherheitsNiveau);
        //logger.info("derivedKeyingMaterial: " + Base64.getEncoder().encodeToString(derivedKeyingMaterial));
        //logger.info("-_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_-");

        //3. derivedKeying material für macKey und sessionkey splitten
        byte[] macKey = new byte[derivedKeyingMaterial.length/2];
        System.arraycopy(derivedKeyingMaterial, 0, macKey, 0, macKey.length);

        byte[] sessionKey = new byte[derivedKeyingMaterial.length/2];
        System.arraycopy(derivedKeyingMaterial, macKey.length, sessionKey, 0, sessionKey.length);

        //logger.info("Erzeugert macKey as base64 String: " + Base64.getEncoder().encodeToString(macKey) + " - Länge in bits: " + macKey.length*8);
        //logger.info("Erzeugert sessionKey as base64 String: " + Base64.getEncoder().encodeToString(sessionKey) + " - Länge in bits: " + sessionKey.length*8);

        //Todo den session key nach Nist mit msb verifizieren

        //todo  1. macdataV generieren
        byte[] macDataV = rsAservice.generateMacDataV(Cv,Cu,sicherheitsNiveau);
        if(macDataV == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new byte[0]);
        }

        //todo  2. macdataV verschlüsseln
        byte[] macTagV;
        try {
            macTagV = rsAservice.generateMAC(macKey,macDataV);
        }catch (Exception exc){
            logger.error("Error aus macDataV konnte kein MacTagV generiert werden");
            exc.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new byte[0]);
        }
        //logger.info("Erzeugter MagTagV: " + Base64.getEncoder().encodeToString(macTagV));

        // 3. macdataV zusammen mit Cv in den Responsebody packen (und an MSA senden siehe unten bei return)
        byte[] responseBody = new byte[Cv.length + macTagV.length];
        System.arraycopy(Cv, 0, responseBody, 0, Cv.length);
        System.arraycopy(macTagV, 0, responseBody, Cv.length, macTagV.length);

        //MacDataU wird an dem Enpoint unten empfangen und verifiziert

        //Todo Z, Zu, Zv sollen alle aus dem Speicher verschwinden nachdem der schlüssel generiert wurde

        //die relevanten parameter in variablen speichern um sie im 2ten endpoint nutzen zu können
        this.Cv = Cv;
        this.Cu = Cu;
        this.sicherheitsNiveau = sicherheitsNiveau;
        this.sessionKey = sessionKey;
        this.macKey = macKey;

        //Cv und magTagV als Response an MSA zurückschicken
        return ResponseEntity.status(HttpStatus.OK).body(responseBody);

    }

    private byte[] Cu;
    private byte[] Cv;
    private int sicherheitsNiveau;
    private byte[] sessionKey;
    private byte[] macKey;

    /**
     * erhält MacTagU von MSB, prüft den Tag und setzt den Session key von AES Service, wenn die Tags übereinstimmen
     * @param receivedMacTagUandTimestamp
     * @return aes verschlüsselte Antwort an MSA welche portrange für die msb instantzen existiert
     * liefert eine leeres byte[] wenn die Tags nicht übereinstimmen oder die portrange nicht gefetcht werden konnte
     */
    @PutMapping("/MacTagU")
    public ResponseEntity<byte[]> verifyMacTagU(@RequestBody byte[] receivedMacTagUandTimestamp){

        byte[] receivedMacTagU = Arrays.copyOfRange(receivedMacTagUandTimestamp, 0, 32);
        long timestamp =  byteToLong(Arrays.copyOfRange(receivedMacTagUandTimestamp, 32, receivedMacTagUandTimestamp.length));

        logger.debug("Erhaltene macTagU: " + Base64.getEncoder().encodeToString(receivedMacTagU));

        //eigene MacDataU generierieren
        byte[] generatedMacDataU = rsAservice.generateMacDataU(this.Cu,this.Cv,this.sicherheitsNiveau);

        //todo MacDataU zu macTagU umwandeln
        byte[] generatedMacTagU;
        try {
            generatedMacTagU = rsAservice.generateMAC(macKey,generatedMacDataU);
        }catch (Exception exc){
            logger.error("Error aus der erzeugte MacDataU konnte kein MacTagU generiert werden");
            //exc.printStackTrace();
            //throw new RuntimeException("macTagU could not be generated. Abort key Establishment");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new byte[0]);
        }

        //der body inhalt für die letzte Response an MSA im keyestablishment process
        //enthält die port range der msb instanzen wenn alles gut läuft und nichts wenn nicht
        String response = timestamp + "." + msbPortRange;
        byte[] encryptedResponse=new byte[0];

        //Generierten MacTagU mit erhaltenem MacTagU vergleichen
        //  wenn beide gleich, dann wird der Sessionkey übernommen
        if(Arrays.equals(generatedMacTagU,receivedMacTagU)){
            //logger.info("Erhaltene MacTagU und generierte MacTagU stimmen überein");
            logger.info("Session key in AES Service wird auf: " + Base64.getEncoder().encodeToString(this.sessionKey) + " gesetzt");
            SecretKey newAESsessionKey = new SecretKeySpec(this.sessionKey, "AES");
            aeSservice.setSessionKey(newAESsessionKey);

            try{
                encryptedResponse = aeSservice.encrypt(response,longToByte(200));
            }catch(Exception exc){
                logger.error("Error sth went wrong encrypting the portrange");
            }

            //logger.info("-------- response unencrypted: " + response);
            //logger.info("-------- ResponseBody encrypted length: " + encryptedResponse.length);

            return ResponseEntity.status(HttpStatus.OK).body(encryptedResponse);//todo weitere api methode um die portrange anfragen zu können
        }

        //logger.info("-------- response unencrypted: " + response);
        //logger.info("-------- ResponseBody encrypted length: " + encryptedResponse.length);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(encryptedResponse);

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

}
