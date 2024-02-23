package de.bachelorarbeit.MicroserviceA.Services;

import de.bachelorarbeit.MicroserviceA.Model.MsaMsbConnectionDetails;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class RSAservice {

    @Value("${known.msb.port}")
    private String firstKnownMsbPort;

    @Value("${msb.host.ip.address}")
    private String msbHostIpAddress;

    @Value("${msb.key.establishment.path}")
    private String msbKeyEstablishmentPath;

    Set<String> receivedMsbPorts = new HashSet<>();
    boolean receivedMsbPortsWereSet =false;

    //Set<String> brokenMsbPorts = new HashSet<>();

    AESservice aeSservice;

    GeneralSendService generalSendService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Autowired
    public RSAservice(GeneralSendService generalSendService, AESservice aeSservice) {
        this.generalSendService = generalSendService;
        this.aeSservice = aeSservice;
    }

    @PostConstruct
    public void init() {
        logger.debug("IP Adresse: " + msbHostIpAddress);
        logger.debug(publicKeyString3072);
        logger.debug(privateKeyString3072);
        logger.debug(publicKeyString15360);
        logger.debug(privateKeyString15360);
        logger.debug(msbPublicKeyString3072);
        logger.debug(msbPublicKeyString15360);

        publicKey3072 = getPublicKeyFromString(publicKeyString3072);
        privateKey3072 = getPrivateKeyFromString(privateKeyString3072);
        PublicKey15360 = getPublicKeyFromString(publicKeyString15360);
        PrivateKey15360 = getPrivateKeyFromString(privateKeyString15360);

        msbPublicKey3072 = getPublicKeyFromString(msbPublicKeyString3072);
        msbPublicKey15360 = getPublicKeyFromString(msbPublicKeyString15360);
    }

    //Todo keys auf private setzen
    //ToDo diese keys müssen mit einer automatischen Methode (nicht in shellcontroller) generiert und bei einer PKI angemeldet werden
    @Value("${msa.public.key.3072}")
    private String publicKeyString3072;

    //is null if sth goes wrong turning the string into public key
    private PublicKey publicKey3072; //todo anstelle jedesmal den String in einen Schlüssel umzuformen wäre es warscheinlich besser dies zum start der Anwendung zu tun
    @Value("${msa.private.key.3072}")
    private String privateKeyString3072;
    private PrivateKey privateKey3072;

    //ToDo es müssen noch keys von der Länge 15360 generiert und gegeneinander getestet werden
    @Value("${msa.public.key.15360}")
    private String publicKeyString15360;

    private PublicKey PublicKey15360;
    @Value("${msa.private.key.15360}")
    private String privateKeyString15360;
    private PrivateKey PrivateKey15360;

    //Todo muss den key von msb bekommen und das zertifikat prüfen
    @Value("${msb.public.key.3072}")
    private String msbPublicKeyString3072;

    private PublicKey msbPublicKey3072;

    //ToDo es müssen noch keys von der Länge 15360 generiert und gegeneinander getestet werden
    @Value("${msb.public.key.15360}")
    private String msbPublicKeyString15360;
    private PublicKey msbPublicKey15360;


    private Logger logger = LoggerFactory.getLogger(RSAservice.class);

    //nimmt ein byte array und verschlüsselt es mit dem übergebenen public key
    //der public key kann sowohl der public key von msa als auch von msb sein
    public byte[] encrypt(byte[] plainText, PublicKey publicKey){
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(plainText);
        }catch (NoSuchAlgorithmException exc){
            logger.error("Error der gewollte Algorithmus zum verschlüsseln wurde nicht gefunden.");
            return null;
        }catch (NoSuchPaddingException exc){
            logger.error("Error etwas mit dem padding.");
            return null;
        }
        catch (InvalidKeyException exc){
            logger.error("Error etwas stimmt mit dem public key nicht beim verschlüsseln");
            return null;
        }
        catch (IllegalBlockSizeException exc){
            logger.error("Error etwas stimmt mit der Blocksize nicht");
            //exc.printStackTrace();
            return null;
        }
        catch (BadPaddingException exc){
            logger.error("Error Das padding ist schlecht");
            return null;
        }
    }

    /**
     * entschlüsselt ein byteArray mit dem private key von msa, der private key wird nach dem sicherheitsniveau ausgesucht
     * @param cipherMessage
     * @param sicherheitsniveau
     * @return null if sth goes wrong while decrypting
     */
    public byte[] decrypt(byte[] cipherMessage, int sicherheitsniveau){

        PrivateKey privateKey;
        switch (sicherheitsniveau){
            case 128: privateKey = this.privateKey3072; break;
            case 256: privateKey = this.PrivateKey15360;break;
            default: return null;
        }

        Cipher cipher;
        try {
            cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(cipherMessage);
        }catch (Exception exc){
            logger.debug("ERROR sth went wrong decrypting the message.");
            return null;
        }

    }

    // Method to convert the RSA public key string (in Base64) to a PublicKey object
    private static PublicKey getPublicKeyFromString(String key){
        try{
            byte[] keyBytes = Base64.getDecoder().decode(key);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        }catch (NoSuchAlgorithmException e){
            //logger.error("Error Algorithm not found to turn publickeyString into public key");
            e.printStackTrace();
            return null;
        }catch (InvalidKeySpecException e){
            //logger.error("Error Something wrong with the key");
            e.printStackTrace();
            return null;
        }
    }

    // Method to convert the RSA private key string (in Base64) to a PrivateKey object
    private static PrivateKey getPrivateKeyFromString(String key){
        try{
            byte[] keyBytes = Base64.getDecoder().decode(key);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(spec);
        }catch(NoSuchAlgorithmException e){
            //logger.error("Error Algorithm not found to turn privatekeyString into public key");
            e.printStackTrace();
            return null;
        }catch(InvalidKeySpecException e){
            //logger.error("Error Something wrong with the key");
            e.printStackTrace();
            return null;
        }
    }

    public void keyEstablishment(int sicherheitsniveau){

        //Establish a Aes connection with the first known msb Instance
        //this will set all the ports of known msb instances by the initial msb instance
        this.initialKeyEstablishment(sicherheitsniveau);
        logger.debug("received msb Ports: " + receivedMsbPorts);

        Set<String> inactivePorts = MsaMsbConnectionDetails.getInactivePorts();
        if(inactivePorts != null){
            for(String msbPort:inactivePorts){
                this.keyEstablishment(msbPort,sicherheitsniveau);
            }
        }

        scheduler.scheduleAtFixedRate(() -> generateAESconnectionWithFailedPorts(sicherheitsniveau), 10, 5, TimeUnit.SECONDS);

    }

    /**
     * Creates shared Session key with the first known msb instance
     * This method will be called every second until the key is established
     * @param sicherheitsNiveau
     */
    private void initialKeyEstablishment(int sicherheitsNiveau){
        boolean aesConnectionIsEstablished = false;
        while(!aesConnectionIsEstablished){
            aesConnectionIsEstablished = generateNewAESconnection(this.firstKnownMsbPort, sicherheitsNiveau);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Try connecting with the given port one time
     * @param portNumber
     * @param sicherheitsNiveau
     * @return
     */
    public boolean keyEstablishment(String portNumber, int sicherheitsNiveau){

        boolean aesConnectionIsEstablished = generateNewAESconnection(portNumber, sicherheitsNiveau);

        if(!aesConnectionIsEstablished){
            logger.warn("Connection to msb instance with port: " + portNumber + " could not be established.");
            return false;
        }
        return true;
    }

    /**
     * For each inactive port a new key establishment process is started
     * @param sicherheitsNiveau
     */
    public void generateAESconnectionWithFailedPorts(int sicherheitsNiveau){
        Set<String> inactivePorts = MsaMsbConnectionDetails.getInactivePorts();
        if(inactivePorts == null || inactivePorts.isEmpty()) return;

        logger.debug("Start key establishment with inactive Ports: " + inactivePorts.toString());

        for (String port: inactivePorts) {
            this.keyEstablishment(port,sicherheitsNiveau);
        }
    }

    public boolean generateNewAESconnection(String portNumber, int sicherheitsniveau){
        byte[] sessionKeyArray = generateSessionKey(portNumber, sicherheitsniveau);
        if(sessionKeyArray == null){
            MsaMsbConnectionDetails.generateMsaMsbConnectionDetails(portNumber,sessionKeyArray,sicherheitsniveau,false);
            return false;
        }
        //generate active Connection with given port
        MsaMsbConnectionDetails.generateMsaMsbConnectionDetails(portNumber,sessionKeyArray,sicherheitsniveau,true);

        //generate inactive Connections with new received ports
        Set<String> newPorts = new HashSet<>(receivedMsbPorts);
        Set<String> knownPorts = MsaMsbConnectionDetails.getKnownPorts();
        if(knownPorts != null && !knownPorts.isEmpty()){
            newPorts.removeAll(MsaMsbConnectionDetails.getKnownPorts());
        }
        for(String port: newPorts){
            MsaMsbConnectionDetails.generateMsaMsbConnectionDetails(port,null,sicherheitsniveau,false);
        }

        return true;
        //aeSservice.addNewAesConnection(msaMsbConnectionDetails);
        //sendMessageService.addMsbPort(portNumber);
    }

    //todo
    /**
     * Handelt mit msb einen gemeinsamen AES-GCM Schlüssel aus 128 oder 256 bits
     * @param sicherheitsNiveau ist das ausgewählte Sicherheitsniveau für die Verschlüsselung des Kommunikationskanals
     *                          entsprechend wird entweder der 3072bits oder 15360bits key verwendet
     *
     * @return 128 oder 256 bit key (für AES-GCM), or null if sth goes wrong
     */
    public byte[] generateSessionKey(String portNumber, int sicherheitsNiveau){

        //Der public key von msb abhängig von sicherheitsniveau auswählen und mit diesem Nachrichten an MSB verschlüsseln
        PublicKey msbPublicKey = this.chooseMsbPublicKey(sicherheitsNiveau);
        if(msbPublicKey == null){
            logger.error("Error sicherheitsniveau of " + sicherheitsNiveau + "bits is not accepted. Try 128 or 256. Session Key generation was terminated.");
            return null;
        }

        //Zu und Cu generieren
        int modulusBitLength = ((RSAPublicKey)msbPublicKey).getModulus().bitLength();
        byte[] Zu = generateZu(modulusBitLength);
        byte[] Cu = this.encrypt(Zu,msbPublicKey);
        if(Cu == null){
            logger.error("ERROR sth went wrong generating Cu");
            return null;
        }

        //Sende Cu an MSB
        //erhalte Cv und MagTagV von MSB als Antwort
        byte[] cvAndMacTagV = this.sendCuReceiveCvAndMagTagV(portNumber, Cu); //todo anstelle von send put send post
        if(cvAndMacTagV == null){
            return null;
        }

        //Cv und MagTagV splitten:
        byte[] Cv = this.getCvFromResponseBody(sicherheitsNiveau,cvAndMacTagV);
        if(Cv == null){
            logger.error("ERROR security level not accepted for extracting Cv");
            return null;
        }
        byte[] receivedMacTagV = this.getMagTagVFromResponseBody(Cv.length,cvAndMacTagV);

        //logger.info("erhaltene CV: " + Base64.getEncoder().encodeToString(Cv));
        //logger.info("erhaltener MagTagV: " + Base64.getEncoder().encodeToString(receivedMacTagV));

        //erhaltene Cv entschlüsseln mit passendem private key
        byte[] Zv = this.decrypt(Cv,sicherheitsNiveau);
        if(Zv == null){
            logger.error("Error Cv could not be decrypted to Zv");
            return null;
        }

        //logger.info("Erhaltene Zv (decrypted Cv) als base64 String: " + Base64.getEncoder().encodeToString(Zv));

        //gemeinsames Geheimnis aus Zu und Zv zusammensetzen
        byte[] Z = new byte[Zu.length + Zv.length];
        //Dies muss in msb genauso sein!
        System.arraycopy(Zu, 0, Z, 0, Zu.length);
        System.arraycopy(Zv, 0, Z, Zu.length, Zv.length);

        //logger.info("Erzeugtes gemeinsames Geheimnis als base64 String: " + Base64.getEncoder().encodeToString(Z));

        //Mac Key und AES session key aus dem erzeugten Geheimnis generieren
        byte[] fixedInfo = this.generateFixedInfo(sicherheitsNiveau);
        if(fixedInfo == null) {
            logger.error("Error fixedInfo konnte nicht generiert werden. KeyEstablishment wird abgebrochen.");
            return null;
        }

        byte[] derivedKeyingMaterial;
        try {
            derivedKeyingMaterial = this.singleStepKDF(Z,fixedInfo,sicherheitsNiveau);
        }catch (NoSuchAlgorithmException e){
            logger.error("Error der Sha Algorithmus für die Erzeugung des derived Keying Materials konnte nicht gefunden werden. KeyEstablishment wird abgebrochen.");
            return null;
        }
        if(derivedKeyingMaterial == null){
            logger.error("Error keying Material could not be derived");
            return null;
        }

        //logger.info("-_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_-");
        //logger.info("Z: " + Base64.getEncoder().encodeToString(Z));
        //logger.info("fixedInfo: " + Base64.getEncoder().encodeToString(fixedInfo));
        //logger.info("sicherheitsniveau: " + sicherheitsNiveau);
        //logger.info("derivedKeyingMaterial: " + Base64.getEncoder().encodeToString(derivedKeyingMaterial));
        //logger.info("-_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_--_-_-_-");

        byte[] macKey = new byte[derivedKeyingMaterial.length/2];
        System.arraycopy(derivedKeyingMaterial, 0, macKey, 0, macKey.length);

        byte[] sessionKey = new byte[derivedKeyingMaterial.length/2];
        System.arraycopy(derivedKeyingMaterial, macKey.length, sessionKey, 0, sessionKey.length);

        logger.debug("Erzeugert macKey mit "+ portNumber + " als base64 String: " + Base64.getEncoder().encodeToString(macKey) + " - Länge in bits: " + macKey.length*8);
        logger.debug("Erzeugert sessionKey mit" + portNumber + " als base64 String: " + Base64.getEncoder().encodeToString(sessionKey) + " - Länge in bits: " + sessionKey.length*8);

        //TODO Verifizieren des Schlüssels mit MSB

        //1. MacDataV erzeugen
        byte[] generatedMacDataV = this.generateMacDataV(Cv,Cu,sicherheitsNiveau);
        if(generatedMacDataV == null){
            logger.error("generated Mac Data V could not be generated");
            return null;
        }

        //logger.info("Erzeugte macDataV: " + Base64.getEncoder().encodeToString(generatedMacDataV));

        //todo 1. erzeuge MagTagV aus dem selber erzeugtem MagDataV
        byte[] generatedMacTagV;
        try {
            generatedMacTagV = this.generateMAC(macKey,generatedMacDataV);
        }catch (Exception exc){
            logger.error("Error aus generatedMacDataV konnte kein generatedMacTagV generiert werden");
            return null;
        }

        //logger.info("Erzeugter MacTagV: " + Base64.getEncoder().encodeToString(generatedMacTagV));
        //logger.info("Erhaltener MacTagV: " + Base64.getEncoder().encodeToString(receivedMacTagV));

        //todo 3. Erzeugte macdataV mit empfangener entschlüsselter macdataV vergleichen
        if(!Arrays.equals(generatedMacTagV,receivedMacTagV)){
            logger.error("ERROR die erhaltene MacTagV stimmt nicht mit mit dem generierten MacTagV überein. Vorgang wird abgebrochen.");
            return null;
        }

        //erzeuge macdataU
        byte[] macDataU = this.generateMacDataU(Cu,Cv,sicherheitsNiveau);
        if(macDataU == null){
            logger.error("ERROR macDataU could not be generated");
            return null;
        }
        //logger.info("Erzeugte macDataU: " + Base64.getEncoder().encodeToString(macDataU));

        //todo 5. macTagU erzeugen
        byte[] macTagU;
        try {
            macTagU = this.generateMAC(macKey,macDataU);
        }catch (Exception exc){
            logger.error("Error aus macDataU konnte kein MacTagU generiert werden");
            //exc.printStackTrace();
            return null;
        }

        long timestamp = System.currentTimeMillis();
        byte[] timestampByteArray = longToByte(timestamp);
        logger.debug("timestamp: " + timestamp);
        byte[] macTagUandTimestamp = new byte[macTagU.length + timestampByteArray.length];
        System.arraycopy(macTagU, 0, macTagUandTimestamp, 0, macTagU.length);
        System.arraycopy(timestampByteArray, 0, macTagUandTimestamp, macTagU.length, timestampByteArray.length);
        //logger.info("length or bytearray: " + macTagUandTimestamp.length);
        logger.debug("timestamp extracted: " + byteToLong(Arrays.copyOfRange(macTagUandTimestamp, 32, macTagUandTimestamp.length)));

        //todo 6. macTagU an MSB senden
        //todo eigene methode für send wie sendCureveiveCVandMagTagV
        String msbMacTagUurl = this.generateMsbMacTagUUrl(portNumber);

        //Wenn:
        //- die tag verifikation in MSB ein fehler liefert, dann kommt 500 zurück
        //- die msb port range nicht gefetcht werden konnte oder die nachricht nicht mit aes verschlüsselt werden konnte kommt 200 zurück aber der body ist leer
        //- die verifikation gut lief und die portrange gefetcht werden konnte dann kommt 200 zurück und im body ist die verschlüsselte Portrange. Die 200 long als byte[] wurde als associated data verwendet.
        ResponseEntity<byte[]> keyVerificationResponse = generalSendService.sendPut(macTagUandTimestamp,msbMacTagUurl);//todo anstelle von send put send post
        if(keyVerificationResponse == null){
            logger.error("Verbindung zu MSB konnte nicht aufgebaut werden.");
            return null;
        }
        else if(keyVerificationResponse.getStatusCode() != HttpStatus.OK){
            logger.error("Error Something went wrong with the verifikation of MagTagU at MSB. vorgang wird abgebrochen");
            return null;
        }else if (keyVerificationResponse.getBody().length == 0) {//session key wurde von der MSB instanz akzeptiert aber die portrange nicht geliefert
            logger.warn("Warn session key was accepted but port range could not be fetched");
            this.updateKnownMsbPorts(this.firstKnownMsbPort);
            //todo anfrage an neue api methode bei diesem msb
        }else{//session key wurde von der MSB instanz akzeptiert und body enthält die portrange
            String msbPortRange;
            String receivedTimestamp;
            String[] timestampAndPortRange;
            try {
                timestampAndPortRange = this.decryptPortRange(keyVerificationResponse.getBody(),longToByte(200),sessionKey).split("\\.");
                receivedTimestamp = timestampAndPortRange[0];
                msbPortRange = timestampAndPortRange[1];
                logger.debug("timestamp: " + receivedTimestamp + " msbPortRange: " + msbPortRange);
            }catch (Exception exc){
                logger.error("ERROR die Entschlüsselung der portrange ist fehlgeschlagen.");
                return null;
            }

            if(timestamp != Long.parseLong(receivedTimestamp)){
                logger.error("ERROR Timestamp does not match");
                return null;
            }

            logger.info("SUCCESS Session generated with MSB at port: " + portNumber + " sessionkey (Base64 String): " + Base64.getEncoder().encodeToString(sessionKey));
            logger.debug("and the received portrange is: " + msbPortRange);

            this.updateKnownMsbPorts(msbPortRange);
        }

        return sessionKey;
    }

    /**
     * liefert den Public key von MSB abhängig von dem gewünschten Sicherheitsniveau
     * @param sicherheitsNiveau in bits, erlaubt sind 128 und 256
     * @return null wenn das Sicherheitsniveau nicht unterstützt wird
     */
    private PublicKey chooseMsbPublicKey(int sicherheitsNiveau) {

        switch(sicherheitsNiveau){
            case 128: return msbPublicKey3072;
            case 256: return msbPublicKey15360;
            default: logger.error("Nicht unterstütztes Sicherheitsniveau von: " + sicherheitsNiveau + " Bits");
                     return null;
        }

    }

    private String decryptPortRange(byte[] portRange, byte[] statusCode,byte[] sessionKey)throws Exception{
        return aeSservice.decrypt(portRange,statusCode,sessionKey);
    }

    private byte[] getCvFromResponseBody(int sicherheitsniveau, byte[] responseBody){
        int cvLengthInByte;
        switch (sicherheitsniveau){
            case 128: cvLengthInByte = 384;break;
            case 256: cvLengthInByte = 1920;break;
            default: return null;
        }

        byte[] Cv = Arrays.copyOfRange(responseBody,0,cvLengthInByte);

        return Cv;
    }

    private byte[] getMagTagVFromResponseBody(int cvLength, byte[] responseBody){
        return Arrays.copyOfRange(responseBody,cvLength,responseBody.length);
    }

    /**
     * Erzeugt ein zufälliges byte array Zu, welches nLen (= modulusBitLength/8) bytes lang ist
     * @param modulusBitLength
     * @return Zufällig generiertes Zu der Länge (nLen - 11) Bytes da 11 Bytes für das Padding verwendet werden.
     */
    private byte[] generateZu(int modulusBitLength){

        int nLen = (modulusBitLength/8) - 11;//-11 Bytes for Padding

        //Todo einen ordentlichen random bit generator verwenden (/dev/random)
        //generiere einen zufälliges byte Array der byte Länge nLen
        //securerandom vs random https://stackoverflow.com/questions/11051205/difference-between-java-util-random-and-java-security-securerandom
        byte[] Zu = new byte[nLen];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(Zu);

        return Zu;
    }

    /**
     * Sendet das in MSA erzeugte mit pubkey von MSB verschlüsselte Geheimniss Cu an MSB
     * @param Cu ist das verschlüsselte geheimnis
     * @return null wenn etwas schiefgegangen ist, das von MSB mit pubKey von MSA verschlüsselte Geheimniss Cv sonst
     */
    private byte[] sendCuReceiveCvAndMagTagV(String portNumber, byte[] Cu){

        String msbCuUrl = this.generateMsbCuUrl(portNumber);

        ResponseEntity<byte[]> responseFromMSB = generalSendService.sendPut(Cu,msbCuUrl);
        if(responseFromMSB == null){
            logger.debug("Etwas ist bei dem Senden von Cu / dem Empfangen von Cv schiefgegangen.");
            return null;
        }
        HttpStatusCode responseStatus = responseFromMSB.getStatusCode();
        byte[] responseBody = responseFromMSB.getBody();
        if(responseStatus == HttpStatus.OK && responseBody != null && responseBody.length > 0) return responseBody;

        logger.debug("Etwas ist bei dem Senden von Cu / dem Empfangen von Cv schiefgegangen.");
        return null;
    }

    private String generateMsbCuUrl(String portNumber){
        return "http://" + msbHostIpAddress + ":" + portNumber + msbKeyEstablishmentPath + "/Cu";
    }

    private String generateMsbMacTagUUrl(String portNumber){
        return "http://" + msbHostIpAddress + ":" + portNumber + msbKeyEstablishmentPath + "/MacTagU";
    }

    private byte[] singleStepKDF(byte[] Z,byte[] fixedInfo,int sicherheitsniveau) throws NoSuchAlgorithmException{
        byte[] counter = {0x00,0x00,0x00,0x01};

        byte[] KDFinput = new byte[counter.length + Z.length + fixedInfo.length];
        System.arraycopy(counter, 0, KDFinput, 0, counter.length);
        System.arraycopy(Z, 0, KDFinput, counter.length, Z.length);
        System.arraycopy(fixedInfo, 0, KDFinput,counter.length + Z.length, fixedInfo.length);

        //Todo check maximale inputlänge für die beiden Sha funktionen
        switch (sicherheitsniveau){
            case 128: return sha256(KDFinput);
            case 256: return sha512(KDFinput);
            default: logger.error("Error Nicht unterstütztes Sicherheitsniveau bei KDF: " + sicherheitsniveau);
                     return null;
        }
    }

    private static byte[] sha256(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(input);
    }
    private static byte[] sha512(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        return digest.digest(input);
    }

    /**
     * MUSS in MSB genau das gleiche Ergebnis liefern wie in MSA!
     * Generiert fixed Info um die KDF durchführen zu können
     * @param sicherheitsniveau
     * @return null wenn das sicherheitsniveau nicht stimmt
     */
    private byte[] generateFixedInfo(int sicherheitsniveau){
        String usedMsaPubkey;
        String usedMsbPubkey;
        switch (sicherheitsniveau){
            case 128: usedMsaPubkey = this.publicKeyString3072;
                      usedMsbPubkey = this.msbPublicKeyString3072;
                      break;
            case 256: usedMsaPubkey = this.publicKeyString15360;
                      usedMsbPubkey = this.msbPublicKeyString15360;
                      break;
            default: logger.error("Error Nicht unterstütztes Sicherheitsniveau bei fixedInfo Erzeugung: " + sicherheitsniveau);
                     return null;
        }

        byte[] firstPart = usedMsaPubkey.getBytes();
        byte[] secondPart = usedMsbPubkey.getBytes();

        byte[] fixedInfo = new byte[firstPart.length + secondPart.length];
        System.arraycopy(firstPart, 0, fixedInfo, 0, firstPart.length);
        System.arraycopy(secondPart, 0, fixedInfo, firstPart.length, secondPart.length);

        return fixedInfo;
    }

    /**
     * Muss in MSA das gleiche ergebnis liefern
     * @param Cv
     * @param Cu
     * @param sicherheitsniveau
     * @return MacDataV
     */
    public byte[] generateMacDataV(byte[] Cv, byte[] Cu, int sicherheitsniveau) {

        String idV;
        String idU;
        switch (sicherheitsniveau){
            case 128: idV = this.msbPublicKeyString3072; idU = this.publicKeyString3072;break;
            case 256: idV = this.msbPublicKeyString15360; idU = this.publicKeyString15360;break;
            default: return null;
        }

        return(generateMacDataVGeneral("KC_2_V",idV,idU,Cv,Cu));
    }

    private byte[] generateMacDataVGeneral(String startString,String idVString,String idUString, byte[] Cv, byte[] Cu){
        byte[] start = startString.getBytes();
        byte[] idV = idVString.getBytes();
        byte[] idU = idUString.getBytes();

        byte[] MacDataV = new byte[start.length + idV.length + idU.length + Cv.length + Cu.length];
        System.arraycopy(start, 0, MacDataV, 0, start.length);
        System.arraycopy(idV,   0, MacDataV, start.length, idV.length);
        System.arraycopy(idU,   0, MacDataV, start.length + idV.length, idU.length);
        System.arraycopy(Cv,    0, MacDataV, start.length + idV.length + idU.length, Cv.length);
        System.arraycopy(Cu,    0, MacDataV, start.length + idV.length + idU.length + Cv.length, Cu.length);

        //logger.info("Generierte MacDataV als Base64 String: " + Base64.getEncoder().encodeToString(MacDataV));

        return MacDataV;

    }

    /**
     * Muss in MSA das gleiche ergebnis liefern
     * @param Cu
     * @param Cv
     * @param sicherheitsniveau
     * @return MacDataU
     */
    public byte[] generateMacDataU(byte[] Cu, byte[] Cv, int sicherheitsniveau) {

        String idU;
        String idV;
        switch (sicherheitsniveau){
            case 128: idU = this.publicKeyString3072; idV = this.msbPublicKeyString3072;break;
            case 256: idU = this.publicKeyString15360; idV = this.msbPublicKeyString15360;break;
            default: return null;
        }

        return(generateMacDataUGeneral("KC_2_V",idU,idV,Cu,Cv));
    }

    private byte[] generateMacDataUGeneral(String startString,String idUString,String idVString, byte[] Cu, byte[] Cv){
        byte[] start = startString.getBytes();
        byte[] idU = idUString.getBytes();
        byte[] idV = idVString.getBytes();

        byte[] MacDataU = new byte[start.length + idU.length + idV.length + Cu.length + Cv.length];
        System.arraycopy(start, 0, MacDataU, 0, start.length);
        System.arraycopy(idU,   0, MacDataU, start.length, idU.length);
        System.arraycopy(idV,   0, MacDataU, start.length + idU.length, idV.length);
        System.arraycopy(Cu,    0, MacDataU, start.length + idU.length + idV.length, Cu.length);
        System.arraycopy(Cv,    0, MacDataU, start.length + idU.length + idV.length + Cu.length, Cv.length);

        //logger.info("Generierte MacDataU als Base64 String: " + Base64.getEncoder().encodeToString(MacDataU));

        return MacDataU;

    }

    /**
     * MUSS in MSA das gleiche Ergebnis liefern
     * Wird dazu verwendet um MacDatav oder MacDataU in einen MacTag umwzuwandeln
     * @param macKey
     * @param macData
     * @return macTagV bzw. macTagU
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    public byte[] generateMAC(byte[] macKey,byte[] macData) throws NoSuchAlgorithmException, InvalidKeyException{
        Mac macAlgorithm = Mac.getInstance("HmacSHA256");

        SecretKey hmacKey = new SecretKeySpec(macKey, "HmacSHA256");

        macAlgorithm.init(hmacKey);

        return macAlgorithm.doFinal(macData);
    }

    //needs to be the same in msb
    private byte[] longToByte(long id){
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(id);
        return buffer.array();
    }

    /**
     * nimmt eine msb portrange port1-port2 oder nur port1 entgegen und updated die ports für die msb instanzen
     * @param msbPortRange
     */
    private void updateKnownMsbPorts(String msbPortRange){

        String[] firstAndLastPort = msbPortRange.split("-");
        String port1 = firstAndLastPort[0];

        if(firstAndLastPort.length == 1){
            this.receivedMsbPorts.add(port1);
            return;
        }

        int port1Int = Integer.parseInt(firstAndLastPort[0]);
        int port2Int = Integer.parseInt(firstAndLastPort[1]);

        int startPort = Math.min(port1Int,port2Int);
        int endPort = Math.max(port1Int,port2Int);

        for(int msbPort = startPort; msbPort <= endPort; ++msbPort){
            this.receivedMsbPorts.add(String.valueOf(msbPort));
        }
    }

    public String getMsbPublicKeyString3072() {
        return msbPublicKeyString3072;
    }

    public String getPublicKeyString3072() {
        return publicKeyString3072;
    }

    public PublicKey getPublicKey3072() {
        return publicKey3072;
    }

    public String getPublicKeyString15360() {
        return publicKeyString15360;
    }

    public PublicKey getPublicKey15360() {
        return PublicKey15360;
    }

    public PublicKey getMsbPublicKey3072() {
        return msbPublicKey3072;
    }

    public String getMsbPublicKeyString15360() {
        return msbPublicKeyString15360;
    }

    public PublicKey getMsbPublicKey15360() {
        return msbPublicKey15360;
    }

    public void renewSessionKeys(int sicherheitsNiveau) {
        List<String> activePorts = MsaMsbConnectionDetails.getActivePorts();
        for(String activePort: activePorts){
            this.keyEstablishment(activePort,sicherheitsNiveau);
        }
    }

    private static long byteToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return buffer.getLong();
    }
}
