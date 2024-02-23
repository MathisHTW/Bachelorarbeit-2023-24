package de.bachelorarbeit.MicroserviceB.DB2.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import de.bachelorarbeit.MicroserviceB.DB1.entities.Einzelanordnung;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;


@Entity
@Table(name="user")
public class User {

    @Id
    //@GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private int id;

    @Column(name="first-name")
    private String firstName;

    @Column(name="last-name")
    private String lastName;

    @JsonProperty("publicKeyString")
    @Column(name="public-key", length = 4000)
    private String publicKeyString;


    //todo needs to be implemented
    //todo anstelle eine einzelanordnung zu bekommen als parameter sollten die einzelnen Daten einfließen
    public byte[] sign(Einzelanordnung einzelanordnung, String password){
        //todo compare entered password with saved password from db
        return null;
    }

    public User() {
    }

    public User(int id, String firstName, String lastName, String publicKey) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.publicKeyString = publicKey;
    }

    public User(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }


    public void setId(int id) {
        this.id = id;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
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
        return publicKeyString;
    }

    public void setPublicKeyString(String publicKeyString) {
        this.publicKeyString = publicKeyString;
    }

    /**
     * Takes the saved Keystring from the user and turns it into a PublicKeyObject
     * @return key as PublicKey object returns null if sth goes wrong
     */
    private PublicKey getPublicKey(){
        String key = this.publicKeyString;

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

    //public void setPublicKey(String publicKey) {
    //    this.publicKeyString = publicKey;
    //}

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", publicKey=" + publicKeyString +
                '}';
    }

    /**
     * nimmt die rohen daten einer einzelanordnung und verifiziert ob die unterschrift tatsächlich zu diesen passt
     * @param betrag
     * @param empfaenger
     * @param timestamp
     * @param prueferId
     * @return true if the signature could be verified false if not or if sth. went wrong
     * @throws SignatureException if sth goes wrong
     */
    public boolean verify(double betrag, String empfaenger, String timestamp, byte[] payload ,int prueferId, byte[] unterschrift) throws SignatureException {

        //get public key for verifying
        PublicKey prueferPublicKey = this.getPublicKey();

        //Setup verifying algorithm
        Signature signature;
        try {
            signature = Signature.getInstance("SHA256withRSA");
        }catch (Exception exc){
            throw new SignatureException("Signature.getInstance(\"SHA256withRSA\") did not work");
        }

        try {
            signature.initVerify(prueferPublicKey);
        } catch (InvalidKeyException e) {
            throw new SignatureException("signature.initVerify(prueferPublicKey) did not work");
        }

        //turn data into byte array:
        byte[] verifiableData = this.verifiableDataToByteArray(betrag,empfaenger,timestamp,prueferId,payload);

        //sign the data
        try {
            signature.update(verifiableData);
        } catch (SignatureException e) {
            throw new SignatureException("signature.update(verifiableData) did not work");
        }

        try {
            return signature.verify(unterschrift);
        } catch (SignatureException e) {
            return false;
        }

    }


    /**
     * turns data into a byte array which can be verified
     * @param betrag
     * @param empfaenger
     * @param timestamp
     * @param prueferId
     * @return
     */
    private byte[] verifiableDataToByteArray(double betrag, String empfaenger, String timestamp, int prueferId, byte[] payload) {
        byte[] betragBytes = ByteBuffer.allocate(Double.BYTES).putDouble(betrag).array();
        byte[] empfaengerBytes = empfaenger.getBytes(StandardCharsets.UTF_8);
        byte[] timestampBytes = timestamp.getBytes(StandardCharsets.UTF_8);
        byte[] prueferIdBytes = ByteBuffer.allocate(Integer.BYTES).putInt(prueferId).array();

        // Combine all byte arrays into a single byte array
        byte[] verifiableData = new byte[betragBytes.length + empfaengerBytes.length + timestampBytes.length + prueferIdBytes.length + payload.length];
        int pos = 0;
        System.arraycopy(betragBytes, 0, verifiableData, pos, betragBytes.length);
        pos += betragBytes.length;
        System.arraycopy(empfaengerBytes, 0, verifiableData, pos, empfaengerBytes.length);
        pos += empfaengerBytes.length;
        System.arraycopy(timestampBytes, 0, verifiableData, pos, timestampBytes.length);
        pos += timestampBytes.length;
        System.arraycopy(prueferIdBytes, 0, verifiableData, pos, prueferIdBytes.length);
        pos += prueferIdBytes.length;
        System.arraycopy(payload, 0, verifiableData, pos, payload.length);

        return verifiableData;
    }
}

