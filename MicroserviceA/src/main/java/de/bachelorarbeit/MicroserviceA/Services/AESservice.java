package de.bachelorarbeit.MicroserviceA.Services;

import de.bachelorarbeit.MicroserviceA.Model.MsaMsbConnectionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.AlgorithmParameterSpec;
import java.util.HashMap;
import java.util.Map;

@Service
public class AESservice {

    /**
     * Bekannte verbindungen mit MSB Instanzen
     * Dient dazu für eine gegebene Portnummer einer MSB Instanz den erzeugten sessionkey zu bekommen
     */
    //Map<String, MsaMsbConnectionDetails> msaMsbConnectionDetailsMap = new HashMap<>();

    //public void addNewAesConnection(MsaMsbConnectionDetails connection){
    //    this.msaMsbConnectionDetailsMap.put(connection.getPortNumber(),connection);
   // }

    //public MsaMsbConnectionDetails getConnectionDetails(String portNumber){
    //    return this.msaMsbConnectionDetailsMap.get(portNumber);
    //}

    //public void removeBrokenConnection(String port){
    //    this.msaMsbConnectionDetailsMap.remove(port);
    //}

    /**
     * Der Initialisierungsvektor IV der für AES im GCM Modus benötigt wird,
     * setzt sich zusammen aus fixedField und invocationField und ist 12byte = 96bits lang
     * fixedField definiert auf welchem Host die Methode ausgeführt wird, ist 4 byte lang, und nimmt die ersten 4 byte des IVs ein
     * invocationField soll auf dem Host nie denselben Wert unter demselben Schlüssel haben, und nimmt die restlichen 8 byte des IVs ein
     */
    //ToDo wenn mehrere MSA existieren sollten, dann sollte sich diese Nummer danach richten um welchen Container es sich handelt oder einfach hostname nehmen
    private String containerNummer = "0";

    //Soll 32bits / 4 byte lang sein
    private String fixedField = "MSA" + containerNummer;

    // soll 96-32bits lang sein, also 64bits also long
    private long invocationField = 0L;

    //TODO sollte mit parametern in .env gleichzeitig für msa und msb gesetzt sein ODER sie einigen sich auf eine Art der Verschlüsselung
    private String AES_MODUS = "AES/GCM/NoPadding";
    private int GCM_TAG_LENGTH= 128;

    Logger logger = LoggerFactory.getLogger(AESservice.class);

    //Quelle https://gist.github.com/patrickfav/7e28d4eb4bf500f7ee8012c4a0cf7bbf
    /**
     * Encrypt a plaintext with given key.
     *
     * @param plaintext      to encrypt (utf-8 encoding will be used)
     * @param associatedData optional, additional (public) data to verify on decryption with GCM auth tag
     * @return encrypted message
     * @throws Exception if anything goes wrong
     */
    public byte[] encrypt(String plaintext, byte[] associatedData,String portNumber) throws Exception {

        SecretKey sessionKey = MsaMsbConnectionDetails.getActiveConnection(portNumber).getSessionKey();

        byte[] iv = this.generateIV(fixedField,portNumber); //NEVER REUSE THIS IV WITH SAME KEY

        //Den Verschlüsselungsalgorithmus festlegen und konfigurieren
        final Cipher cipher = Cipher.getInstance(AES_MODUS);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv); //96 bit auth tag length
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey, parameterSpec);

        if (associatedData != null) {
            cipher.updateAAD(associatedData);
        }

        //Nachricht verschlüsseln
        byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        //IV und verschlüsselte Nachricht zusammensetzen
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
        byteBuffer.put(iv);
        byteBuffer.put(cipherText);
        return byteBuffer.array();
    }

    private byte[] generateIV(String fixedField, String portnumber){

        //fixedField und invocationField in byte Arrays umwandeln um aus ihnen eine IV zu erzeugen
        byte[] fixedFieldArray = fixedField.getBytes(StandardCharsets.UTF_8);
        //logger.info("fixedField als byteArray = " + new String(fixedFieldArray,StandardCharsets.UTF_8) + " - length = " + fixedFieldArray.length);

        MsaMsbConnectionDetails connection = MsaMsbConnectionDetails.getActiveConnection(portnumber);
        byte[] invocationFieldArray = ByteBuffer.allocate(8).putLong(connection.getInvocationField()).array();
        //logger.info("verwendetes invocationField als long: " + invocationField);
        //logger.info("invocationFieldArray als String nach UTF-8kodierung: " + new String(invocationFieldArray,StandardCharsets.UTF_8) + " - length = " + invocationFieldArray.length);

        //für den nächsten IV um 1 erhöhen, damit nie derselbe IV mit demselben Schlüssel verwendet wird
        //this.invocationField++;
        connection.incInvocationField();
        //logger.info("invocationField increased by 1. invocationfield =  " + this.invocationField);


        byte[] IV = new byte[12];
        System.arraycopy(fixedFieldArray, 0, IV, 0, 4);
        System.arraycopy(invocationFieldArray, 0, IV, 4, 8);

        //logger.info("erzeugter IV für die Verschlüsselung mit AES GCM: " + new String(IV, StandardCharsets.UTF_8) + " - length = " + IV.length);

        return IV;

    }

    //ToDo sollte als parameter in dockercompose definiert werden
    private final int GCM_IV_LENGTH = 12; //12bytes = 96bits

    //https://gist.github.com/patrickfav/7e28d4eb4bf500f7ee8012c4a0cf7bbf
    /**
     * Decrypts encrypted message
     *
     * @param cipherMessage  iv with ciphertext
     * @param associatedData optional, additional (public) data to verify on decryption with GCM auth tag
     * @return original plaintext
     * @throws Exception if anything goes wrong
     */
    public String decrypt(byte[] cipherMessage, byte[] associatedData, String portNumber) throws Exception {

        SecretKey sessionKey = MsaMsbConnectionDetails.getActiveConnection(portNumber).getSessionKey();

        return this.decrypt(cipherMessage,associatedData,sessionKey);
    }

    /**
     * wird dazu genutzt die portrange welche von der ersten Instanz von MSB zurückgeschickt wird zu entschlüsseln
     * @param cipherMessage
     * @param associatedData
     * @param sessionKeyByte
     * @return
     * @throws Exception
     */
    public String decrypt(byte[] cipherMessage, byte[] associatedData, byte[] sessionKeyByte) throws Exception {

        SecretKey sessionKey =  new SecretKeySpec(sessionKeyByte, "AES");

        return this.decrypt(cipherMessage,associatedData,sessionKey);

    }

    private String decrypt(byte[] cipherMessage, byte[] associatedData, SecretKey sessionKey) throws Exception{

        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        //use first 12 bytes for iv
        //taglength = 128bits
        AlgorithmParameterSpec gcmIv = new GCMParameterSpec(GCM_TAG_LENGTH, cipherMessage, 0, GCM_IV_LENGTH);
        cipher.init(Cipher.DECRYPT_MODE, sessionKey, gcmIv);

        if (associatedData != null) {
            cipher.updateAAD(associatedData);
        }
        //use everything from 12 bytes on as ciphertext
        byte[] plainText = cipher.doFinal(cipherMessage, GCM_IV_LENGTH, cipherMessage.length - GCM_IV_LENGTH);

        return new String(plainText, StandardCharsets.UTF_8);
    }

}
