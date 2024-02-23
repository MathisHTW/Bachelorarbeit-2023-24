package de.bachelorarbeit.MicroserviceA.DB2.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.bachelorarbeit.MicroserviceA.DB1.entities.Einzelanordnung;
import jakarta.persistence.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Entity
@Table(name="user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private int id;

    @Column(name="first-name")
    private String firstName;

    @Column(name="last-name")
    private String lastName;

    @JsonIgnore
    @Column(name="password")
    private String password;

    @JsonIgnore
    @Column(name="private-key", length = 4000)
    private String privateKey;

    @Column(name="public-key", length = 4000)
    private String publicKey;

    public User() {
    }

    public User(String firstName, String lastName, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.password = password;
        this.setKeyPair(3072);//equals security level of 128Bits
    }

    /**
     * Generates a private and public key pair for the user
     * turns the objects into a byte array and that byte array into a Base64 encoded String
     * and sets the fields privateKeyString and publickeyString of this instance
     * @param keyLengthInBits
     */
    private void setKeyPair(int keyLengthInBits) {
        KeyPairGenerator keyPairGenerator;
        try{
            keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        }catch (NoSuchAlgorithmException e){
            return;
        }

        keyPairGenerator.initialize(keyLengthInBits);

        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        String publicKeyString = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        String privateKeyString = Base64.getEncoder().encodeToString(privateKey.getEncoded());

        this.setPrivateKey(privateKeyString);
        this.setPublicKey(publicKeyString);

    }

    private void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    private void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public int getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPublicKeyString() {
        return publicKey;
    }

    /**
     * nimmt die rohen daten einer einzelanordnung und unterschreibt diese einschließlich der eigenen id
     * @param betrag
     * @param empfaenger
     * @param timestamp
     * @param prueferId
     * @return unterschrift vom pruefer mit der id prueferId, returns null if sth goes wrong
     */
    //todo compare entered password with saved password from db
    public byte[] sign(double betrag, String empfaenger, String timestamp, int prueferId, byte[] payload) {

        //get private key for signing
        PrivateKey prueferPrivateKey = getPrivateKey();

        //Setup signing algorithm
        Signature signature;
        try {
            signature = Signature.getInstance("SHA256withRSA");
        }catch (Exception exc){
            return null;
        }

        try {
            signature.initSign(prueferPrivateKey);
        } catch (InvalidKeyException e) {
            return null;
        }

        //turn data into byte array:
        byte[] signableData = this.singableDataToByteArray(betrag,empfaenger,timestamp,prueferId,payload);

        //sign the data
        try {
            signature.update(signableData);
        } catch (SignatureException e) {
            return null;
        }

        try {
            return signature.sign();
        } catch (SignatureException e) {
            return null;
        }

    }

    /**
     * Takes the saved Keystring field of this instance and turns it back into a PrivateKeyObject
     * @return key as PrivateKey object returns null if sth goes wrong
     */
    private PrivateKey getPrivateKey(){
        String privateKeyString = this.privateKey;

        try{
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyString);
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
     * turns parameter data into a byte array which can be signed
     * @param betrag
     * @param empfaenger
     * @param timestamp
     * @param prueferId
     * @return byte array that combines the parameters values
     */
    private byte[] singableDataToByteArray(double betrag, String empfaenger, String timestamp, int prueferId,byte[] payload) {
        byte[] betragBytes = ByteBuffer.allocate(Double.BYTES).putDouble(betrag).array();
        byte[] empfaengerBytes = empfaenger.getBytes(StandardCharsets.UTF_8);
        byte[] timestampBytes = timestamp.getBytes(StandardCharsets.UTF_8);
        byte[] prueferIdBytes = ByteBuffer.allocate(Integer.BYTES).putInt(prueferId).array();

        // Combine all byte arrays into a single byte array
        byte[] signableData = new byte[betragBytes.length + empfaengerBytes.length + timestampBytes.length + prueferIdBytes.length + payload.length];
        int pos = 0;
        System.arraycopy(betragBytes, 0, signableData, pos, betragBytes.length);
        pos += betragBytes.length;
        System.arraycopy(empfaengerBytes, 0, signableData, pos, empfaengerBytes.length);
        pos += empfaengerBytes.length;
        System.arraycopy(timestampBytes, 0, signableData, pos, timestampBytes.length);
        pos += timestampBytes.length;
        System.arraycopy(prueferIdBytes, 0, signableData, pos, prueferIdBytes.length);
        pos += prueferIdBytes.length;
        System.arraycopy(payload, 0, signableData, pos, payload.length);


        return signableData;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", password='" + password + '\'' +
                ", privateKey=" + privateKey +
                ", publicKey=" + publicKey +
                '}';
    }

    /**
     * Not really used in this ms yet
     * Takes the saved Keystring from the user and turns it into a PublicKeyObject
     * @return key as PublicKey object returns null if sth goes wrong
     */
    private PublicKey getPublicKey(){
        String publicKeyString = this.publicKey;

        try{
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyString);
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


}
