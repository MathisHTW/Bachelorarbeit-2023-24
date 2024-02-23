package de.bachelorarbeit.MicroserviceB.Services;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class RSAservice {

    @PostConstruct
    public void init() {

        logger.debug(publicKeyString3072);
        logger.debug(privateKeyString3072);
        logger.debug(publicKeyString15360);
        logger.debug(privateKeyString15360);
        logger.debug(msaPublicKeyString3072);
        logger.debug(msaPublicKeyString15360);

        publicKey3072 = getPublicKeyFromString(publicKeyString3072);
        privateKey3072 = getPrivateKeyFromString(privateKeyString3072);
        PublicKey15360 = getPublicKeyFromString(publicKeyString15360);
        PrivateKey15360 = getPrivateKeyFromString(privateKeyString15360);

        msaPublicKey3072 = getPublicKeyFromString(msaPublicKeyString3072);
        msaPublicKey15360 = getPublicKeyFromString(msaPublicKeyString15360);
    }

    //ToDo es müssen noch keys von der Länge 15360 generiert und gegeneinander getestet werden

    //ToDo diese keys müssen mit einer automatischen Methode (nicht in shellcontroller) generiert und bei einer PKI angemeldet werden
    @Value("${msb.public.key.3072}")
    private String publicKeyString3072;
    private PublicKey publicKey3072;

    @Value("${msb.private.key.3072}")
    private String privateKeyString3072;

    private PrivateKey privateKey3072;

    //ToDo es müssen noch keys von der Länge 15360 generiert und gegeneinander getestet werden
    @Value("${msb.public.key.15360}")
    private String publicKeyString15360;

    private PublicKey PublicKey15360;

    @Value("${msb.private.key.15360}")
    private String privateKeyString15360;
    private PrivateKey PrivateKey15360;

    //Todo muss den key von msa bekommen und das zertifikat prüfen
    @Value("${msa.public.key.3072}")
    String msaPublicKeyString3072;
    private PublicKey msaPublicKey3072;

    //ToDo es müssen noch keys von der Länge 15360 generiert und gegeneinander getestet werden
    @Value("${msa.public.key.15360}")
    private String msaPublicKeyString15360;
    private PublicKey msaPublicKey15360;

    Logger logger = LoggerFactory.getLogger(RSAservice.class);

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
            exc.printStackTrace();
            return null;
        }
        catch (BadPaddingException exc){
            logger.error("Error Das padding ist schlecht");
            return null;
        }
    }


    /**
     * entschlüsselt ein byteArray mit dem private key von msb, der private key wird nach dem sicherheitsniveau ausgesucht
     * @param cipherMessage
     * @param sicherheitsniveau
     * @return null if sth goes wrong while decrypting
     */
    public byte[] decrypt(byte[] cipherMessage, int sicherheitsniveau){

        PrivateKey privateKey;
        switch (sicherheitsniveau){
            case 128: privateKey = this.privateKey3072; break;
            case 256: privateKey = this.PrivateKey15360;break;
            default: throw new IllegalArgumentException("Nicht unterstütztes Sicherheitsniveau: " + sicherheitsniveau);
        }

        Cipher cipher;
        try {
            cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(cipherMessage);
        }catch (Exception exc){
            logger.error("ERROR sth went wrong decrypting the message.");
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

    /**
     * Erzeugt ein zufälliges byte array Zv, welches nLen (= keylength/8) bytes lang ist
     * @param keylength akzeptiert nur keylength von 3072 oder 15360 !
     */
    public byte[] generateZv(int keylength) throws IllegalArgumentException{

        int nLen; //die Länge des verwendeted modulos n in byte
        switch (keylength){
            case 3072: nLen=384-11;break; //11 byte padding beim verschlüsseln
            case 15360: nLen=1920-11;break; //11 byte padding  beim verschlüsseln
            default: throw new IllegalArgumentException("angegebene Keylength für die Generierung von Zv wird nicht unterstützt: " + keylength);
        }

        //Todo einen ordentlichen random bit generator verwenden (/dev/random)
        //generiere einen zufälliges byte Array der byte Länge nLen
        //securerandom vs random https://stackoverflow.com/questions/11051205/difference-between-java-util-random-and-java-security-securerandom
        byte[] Zv = new byte[nLen];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(Zv);

        return Zv;
    }

    public byte[] singleStepKDF(byte[] Z,byte[] fixedInfo,int sicherheitsniveau) throws NoSuchAlgorithmException{
        byte[] counter = {0x00,0x00,0x00,0x01};

        //Todo fix arraycopy
        byte[] KDFinput = new byte[counter.length + Z.length + fixedInfo.length];
        System.arraycopy(counter, 0, KDFinput, 0, counter.length);
        System.arraycopy(Z, 0, KDFinput, counter.length, Z.length);
        System.arraycopy(fixedInfo, 0, KDFinput, counter.length + Z.length, fixedInfo.length); //todo Z.length muss Z.lenght + counter.length sein

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
    public byte[] generateFixedInfo(int sicherheitsniveau){
        String usedMsaPubkey;
        String usedMsbPubkey;
        switch (sicherheitsniveau){
            case 128: usedMsaPubkey = this.msaPublicKeyString3072;
                usedMsbPubkey = this.publicKeyString3072;
                break;
            case 256: usedMsaPubkey = this.msaPublicKeyString15360;
                usedMsbPubkey = this.publicKeyString15360;
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
            case 128: idV = this.publicKeyString3072; idU = this.msaPublicKeyString3072;break;
            case 256: idV = this.publicKeyString15360; idU = this.msaPublicKeyString15360;break;
            default: logger.error("sicherheitsniveau in generate Mac Data v nicht unterstützt: " + sicherheitsniveau);
            return null;
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
            case 128: idU = this.msaPublicKeyString3072; idV = this.publicKeyString3072;break;
            case 256: idU = this.msaPublicKeyString15360; idV = this.publicKeyString15360;break;
            default: throw new RuntimeException("sicherheitsniveau in generate Mac Data v nicht unterstützt: " + sicherheitsniveau);
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


    /**
     * Returns the security level for the given Cu
     * @param keyLength
     * @return securitylevel for accepted lenghts of Cu. returns -1 if the length of cu does not fit a security level
     */
    //todo add more security levels
    public int getSecurityLevel(int keyLength){
        switch (keyLength){
            case 3072: return 128;
            case 15360: return 256;
            default: return -1;
        }
    }

    public boolean securityLevelIsValid(int securityLevel){
        switch (securityLevel){
            case 128: return true;
            case 256: return true;
            default: return false;
        }
    }

    public PublicKey getMsaPublicKey(int securitylevel){
        switch (securitylevel){
            case 128: return this.getMsaPublicKey3072();
            case 256: return this.getMsaPublicKey15360();
            default: logger.error("MSA public key for security level " + securitylevel + "does not exist");
                     return null;
        }
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

    public String getMsaPublicKeyString3072() {
        return msaPublicKeyString3072;
    }

    public PublicKey getMsaPublicKey3072() {
        return msaPublicKey3072;
    }

    public String getMsaPublicKeyString15360() {
        return msaPublicKeyString15360;
    }

    public PublicKey getMsaPublicKey15360() {
        return msaPublicKey15360;
    }

}
