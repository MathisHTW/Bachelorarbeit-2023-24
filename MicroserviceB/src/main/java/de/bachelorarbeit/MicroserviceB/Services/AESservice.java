package de.bachelorarbeit.MicroserviceB.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;


import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.AlgorithmParameterSpec;

@Service
public class AESservice {

    //ToDo sollte als parameter in dockercompose definiert werden
    private final int GCM_IV_LENGTH = 12; //12bytes = 96bits

    //TODO create the sessionkey with RSA Algorithm
    // dieser key hat einen static default wert zum testen, wird aber mit jeden generateSessionkey aus RSAservice neu gesetzt
    private SecretKey sessionKey = generateSessionKey();

    public void setSessionKey(SecretKey sessionKey) {
        this.sessionKey = sessionKey;
    }

    //TODO sollte nur zum testen verwendet werden und später wieder gelöscht werden
    public SecretKey getSessionKey() {
        return sessionKey;
    }

    Logger logger = LoggerFactory.getLogger(AESservice.class);

    //https://gist.github.com/patrickfav/7e28d4eb4bf500f7ee8012c4a0cf7bbf
    /**
     * Decrypts encrypted message (see {@link #encrypt(String, byte[])}).
     *
     * @param cipherMessage  iv with ciphertext
     * @param associatedData optional, additional (public) data to verify on decryption with GCM auth tag
     * @return original plaintext
     * @throws Exception if anything goes wrong
     */
    public String decrypt(byte[] cipherMessage, byte[] associatedData) throws Exception {
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        //use first 12 bytes for iv
        //taglength = 96bits
        AlgorithmParameterSpec gcmIv = new GCMParameterSpec(GCM_TAG_LENGTH, cipherMessage, 0, GCM_IV_LENGTH);
        cipher.init(Cipher.DECRYPT_MODE, this.sessionKey, gcmIv);

        if (associatedData != null) {
            cipher.updateAAD(associatedData);
        }
        //use everything from 12 bytes on as ciphertext
        byte[] plainText = cipher.doFinal(cipherMessage, GCM_IV_LENGTH, cipherMessage.length - GCM_IV_LENGTH);

        return new String(plainText, StandardCharsets.UTF_8);
    }

    //ToDO muss an RSA generation angepasst werden
    private SecretKey generateSessionKey(){

        byte[] seed = "HelloThere".getBytes();

        try {
            // Use SHA-256 as the hashing algorithm
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Compute the hash of the seed bytes
            byte[] hash = digest.digest(seed);

            // Use the first 16 bytes (128 bits) of the hash as the AES key
            byte[] aesKeyBytes = Arrays.copyOf(hash, 16); // 16 bytes for a 128-bit key

            // Generate AES SecretKey from derived key bytes
            return new SecretKeySpec(aesKeyBytes, "AES");

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            logger.error("ERROR Session key could not be generated!");
            // Handle NoSuchAlgorithmException
        }
        return null;
    }

    //ToDo wenn mehrere MSA existieren sollten, dann sollte sich diese Nummer danach richten um welchen Container es sich handelt oder einfach hostname nehmen
    private String containerNummer = "0";

    //Soll 32bits / 4 byte lang sein
    private String fixedField = "MSB" + containerNummer;

    // soll 96-32bits lang sein, also 64bits also long
    private long invocationField = 0L;

    //TODO sollte mit parametern in .env gleichzeitig für msa und msb gesetzt sein ODER sie einigen sich auf eine Art der Verschlüsselung
    private String AES_MODUS = "AES/GCM/NoPadding";
    private int GCM_TAG_LENGTH= 128;

    //Quelle https://gist.github.com/patrickfav/7e28d4eb4bf500f7ee8012c4a0cf7bbf
    /**
     * Encrypt a plaintext with given key.
     *
     * @param plaintext      to encrypt (utf-8 encoding will be used)
     * @param associatedData optional, additional (public) data to verify on decryption with GCM auth tag
     * @return encrypted message
     * @throws Exception if anything goes wrong
     */
    public byte[] encrypt(String plaintext, byte[] associatedData) throws Exception {

        byte[] iv = this.generateIV(fixedField,invocationField); //NEVER REUSE THIS IV WITH SAME KEY

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

    private byte[] generateIV(String fixedField, long invocationField){

        //fixedField und invocationField in byte Arrays umwandeln um aus ihnen eine IV zu erzeugen
        byte[] fixedFieldArray = fixedField.getBytes(StandardCharsets.UTF_8);
        //logger.info("fixedField als byteArray = " + new String(fixedFieldArray,StandardCharsets.UTF_8) + " - length = " + fixedFieldArray.length);

        byte[] invocationFieldArray = ByteBuffer.allocate(8).putLong(invocationField).array();
        //logger.info("verwendetes invocationField als long: " + invocationField);
        //logger.info("invocationFieldArray als String nach UTF-8kodierung: " + new String(invocationFieldArray,StandardCharsets.UTF_8) + " - length = " + invocationFieldArray.length);

        //für den nächsten IV um 1 erhöhen, damit nie derselbe IV mit demselben Schlüssel verwendet wird
        this.invocationField++;
        //logger.info("invocationField increased by 1. invocationfield =  " + this.invocationField);


        byte[] IV = new byte[12];
        System.arraycopy(fixedFieldArray, 0, IV, 0, 4);
        System.arraycopy(invocationFieldArray, 0, IV, 4, 8);

        //logger.info("erzeugter IV für die Verschlüsselung mit AES GCM: " + new String(IV, StandardCharsets.UTF_8) + " - length = " + IV.length);

        return IV;

    }

}
