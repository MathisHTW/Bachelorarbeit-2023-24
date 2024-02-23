package de.bachelorarbeit.MicroserviceA.Model;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Enthält Informationen über eine Verbindung zwischen MSA und einer MSB instanz
 * Wird in AES service in einer Hashmap an die Portnummer der jeweiligen MSB Instanz gekoppelt
 */
public class MsaMsbConnectionDetails {

    private static Map<String,MsaMsbConnectionDetails> knownConnections = new HashMap<>();

    public static Map<String, MsaMsbConnectionDetails> getKnownConnections() {
        return knownConnections;
    }

    public static MsaMsbConnectionDetails getKnownConnection(String portNumber){
        return knownConnections.get(portNumber);
    }

    public static Set<String> getKnownPorts(){
        return knownConnections.keySet();
    }

    private static volatile Map<String,MsaMsbConnectionDetails> activeConnections = new HashMap<>();

    public static Map<String, MsaMsbConnectionDetails> getActiveConnections() {
        return activeConnections;
    }
    
    public static List<String> getActivePorts(){
        synchronized (activeConnections) {
            return new ArrayList<>(activeConnections.keySet());
        }
    }

    public static MsaMsbConnectionDetails getActiveConnection(String portNumber){
        return activeConnections.get(portNumber);
    }

    public static Set<String> getInactivePorts(){
        Set<String> inactivePorts = new HashSet<>(getKnownPorts());
        if(inactivePorts == null) return null;
        inactivePorts.removeAll(getActivePorts());
        return inactivePorts;
    }

    /**
     * MSB Instanz Portnummer mit der die Verbindung besteht
     */
    private String portNumber;

    /**
     * Der mit der Instanz ausgehandelte Sessionkey als Secret key Objekt
     */
    private SecretKey sessionKey;

    private long invocationField;

    /**
     * Datum und Zeit wann der aktuelle SessionKey ausgehandelt wurde
     */
    private LocalDateTime sessionKeyEstablishmentDate;

    /**
     * Zählt wieviele Nachrichten an die MSB Instanz nicht übertragen werden konnten
     */
    private int lostMessagesCounter;

    private boolean isActive;

    public int securityLevel;



    public int getLostMessagesCounter() {
        return lostMessagesCounter;
    }

    public void setLostMessagesCounter(int lostMessagesCounter) {
        this.lostMessagesCounter = lostMessagesCounter;
    }

    public SecretKey getSessionKey() {
        return sessionKey;
    }

    public LocalDateTime getSessionKeyEstablishmentDate() {
        return sessionKeyEstablishmentDate;
    }

    public String getPortNumber() {
        return portNumber;
    }

    private MsaMsbConnectionDetails(String portNumber, byte[] sessionKeyRaw, int securityLevel, boolean isActive) {
        this.portNumber = portNumber;
        this.setSessionKey(sessionKeyRaw);
        this.invocationField =0L;
        this.sessionKeyEstablishmentDate = LocalDateTime.now();
        this.lostMessagesCounter = 0;
        this.isActive = isActive;
        this.securityLevel = securityLevel;
    }

    public static void generateMsaMsbConnectionDetails(String portNumber, byte[] sessionKeyRaw, int securityLevel, boolean isActive){
        MsaMsbConnectionDetails msaMsbConnectionDetails = new MsaMsbConnectionDetails(portNumber,sessionKeyRaw,securityLevel,isActive);
        if(msaMsbConnectionDetails.isActive){
            activeConnections.put(portNumber,msaMsbConnectionDetails);
        }
        knownConnections.put(portNumber,msaMsbConnectionDetails);
    }

    /**
     * Turns byte array session key into an aes secret key object for this instance
     * @param sessionKeyRaw the raw session key as byte[]
     */
    public void setSessionKey(byte[] sessionKeyRaw) {
        if (sessionKeyRaw == null) {
            this.sessionKey = null;
            return;
        }

        this.sessionKey =  new SecretKeySpec(sessionKeyRaw, "AES");
        this.sessionKeyEstablishmentDate = LocalDateTime.now();
    }

    public void incLostMessagesCnt(){
        this.lostMessagesCounter++;
        if(this.lostMessagesCounter > 24){
            this.isActive = false;
            activeConnections.remove(this.portNumber);
        }
    }

    public void incInvocationField(){
        this.invocationField = invocationField + 1;
        if(this.invocationField > Long.MAX_VALUE/2){
            this.isActive=false;
            activeConnections.remove(this.portNumber);
        }
    }

    public void resetLostMessagesCounter(){
        this.lostMessagesCounter = 0;
    }

    public boolean isActive() {
        return isActive;
    }

    public int getSecurityLevel() {
        return securityLevel;
    }

    @Override
    public String toString() {
        return "MsaMsbConnectionDetails{" +
                "portNumber='" + portNumber + '\'' +
                ", sessionKey=" + sessionKey +
                ", sessionKeyEstablishmentDate=" + sessionKeyEstablishmentDate +
                ", lostMessagesCounter=" + lostMessagesCounter +
                '}';
    }

    public long getInvocationField() {
        return this.invocationField;
    }
}
