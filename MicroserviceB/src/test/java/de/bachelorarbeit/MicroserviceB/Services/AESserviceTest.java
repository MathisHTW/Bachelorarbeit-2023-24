package de.bachelorarbeit.MicroserviceB.Services;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AESserviceTest {

    //TODO sollte mit parametern in .env gleichzeitig für msa und msb gesetzt sein ODER sie einigen sich auf eine Art der Verschlüsselung
    private String AES_MODUS = "AES/GCM/NoPadding";
    private int GCM_TAG_LENGTH= 96;

    //ToDo sollte als parameter in dockercompose definiert werden
    private final int GCM_IV_LENGTH = 12; //12bytes = 96bits

    //ToDo wenn mehrere MSA existieren sollten, dann sollte sich diese Nummer danach richten um welchen Container es sich handelt oder einfach hostname nehmen
    private String containerNummer = "0";

    //Soll 32bits / 4 byte lang sein
    private String fixedField = "MSA" + containerNummer;

    // soll 96-32bits lang sein, also 64bits also long

    // soll 96-32bits lang sein, also 64bits also long
    private long invocationField = 0L;

    //TODO create the sessionkey with RSA Algorithm
    private SecretKey sessionKey = generateSessionKey();

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
            // Handle NoSuchAlgorithmException
        }
        return null;
    }

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

        byte[] invocationFieldArray = ByteBuffer.allocate(8).putLong(invocationField).array();


        //für den nächsten IV um 1 erhöhen, damit nie derselbe IV mit demselben Schlüssel verwendet wird
        this.invocationField++;

        byte[] IV = new byte[12];
        System.arraycopy(fixedFieldArray, 0, IV, 0, 4);
        System.arraycopy(invocationFieldArray, 0, IV, 4, 8);

        return IV;

    }

    public String decrypt(byte[] cipherMessage, byte[] associatedData) throws Exception {
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        //use first 12 bytes for iv
        //taglength = 96bits
        AlgorithmParameterSpec gcmIv = new GCMParameterSpec(96, cipherMessage, 0, GCM_IV_LENGTH);
        cipher.init(Cipher.DECRYPT_MODE, this.sessionKey, gcmIv);

        if (associatedData != null) {
            cipher.updateAAD(associatedData);
        }
        //use everything from 12 bytes on as ciphertext
        byte[] plainText = cipher.doFinal(cipherMessage, GCM_IV_LENGTH, cipherMessage.length - GCM_IV_LENGTH);

        return new String(plainText, StandardCharsets.UTF_8);
    }

    @Test
    public void checkFakeId(){

        //msb erhält einen put request mit einer korrekte Id und einen Request bei dem ein man in the middle die id geändert hat
        long realId = 100;
        long fakeId = 666;

        byte[] realIdArray = longToByte(realId);
        byte[] fakeIdArray = longToByte(fakeId);

        //Der plaintext wird nur mit dem key und der richtigen id verschlüsselt
        String plaintext = "hello there friend :)";
        byte[] ciphertext;
        try{
            ciphertext = this.encrypt(plaintext,realIdArray); //might throw exception
        }catch (Exception exc){
            System.out.println("Sth went wrong encrypting");
            exc.printStackTrace();
            return;
        }


        //entschlüsseln des textes mit der richtigen Id sollte funktionieren
        try{
            assertEquals(plaintext,this.decrypt(ciphertext,realIdArray));
        }catch (Exception exc){
            System.out.println("Sth went wrong decrypting with the correct id!");
            exc.printStackTrace();
            return;
        }

        //entschlüsseln des textes mit der falschen Id sollte nicht funktionieren
        assertThrows(Exception.class, () -> {this.decrypt(ciphertext,fakeIdArray);});

        /*
        try{
            assertEquals(plaintext,this.decrypt(ciphertext,fakeIdArray));
        }catch (Exception exc){
            System.out.println("Sth went wrong decrypting with the wrong id!");
            exc.printStackTrace();
        }
         */

    }

    //needs to be the same in msa
    //die id aus dem put path muss in ein byte array umgewandelt werden um von der
    //decrypt methode als associatied data auf integrität geprüft werden zu können
    private byte[] longToByte(long id){
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(id);
        return buffer.array();
    }

}