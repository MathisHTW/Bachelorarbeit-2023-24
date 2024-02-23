package de.bachelorarbeit.MicroserviceA.DB1.entities;

import jakarta.persistence.*;

import java.util.Base64;
import java.util.Random;

/**
 * needs getter methods for all the fields that should be written into the JSON representation of the objekt of this class
 */
@Entity
@Table(name="einzelanordnung")
public class Einzelanordnung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private long id;

    @Column(name="time-stamp")
    private String timeStamp;

    //todo weitere Daten für den Inhalt der Einzelanordnung
    @Column(name="betrag")
    private double betrag;

    @Column(name="empfaenger")
    private String empfaenger;

    @Column(name="pruefer1-id")
    private int pruefer1Id;
    @Column(name="pruefer2-id")
    private int pruefer2Id;

    @Column(name="unterschrift-pruefer-1", length = 5000)
    private byte[] unterschrift1;

    @Column(name="unterschrift-pruefer-2", length = 5000)
    private byte[] unterschrift2;

    @Column(name="was-received-by-MSB")
    private boolean wasReceivedByMSB;

    @Column(name="flagged-by-MSB")
    private boolean wrongSignatures;

    @Column(name="payload",length = 16000)
    private byte[] payload;

    public byte[] getPayload() {
        return payload;
    }

    public Einzelanordnung() {
    }

    /**
     * Erzeugt eine vollständige Einzelanordnung die in der DB1 gespeichert werden kann.
     * Das erzeugen und speichern von Einzelanordnungen passiert mit Hilfe der Klasse EinzelanordnungGenerator
     * @param betrag
     * @param empfaenger
     * @param timeStamp
     * @param pruefer1Id
     * @param pruefer2Id
     * @param unterschrift1
     * @param unterschrift2
     */
    public Einzelanordnung(double betrag, String empfaenger,String timeStamp, int pruefer1Id, int pruefer2Id, byte[] unterschrift1, byte[] unterschrift2, byte[] payload) {
        this.betrag = betrag;
        this.empfaenger = empfaenger;
        this.timeStamp = timeStamp;

        this.wasReceivedByMSB=false;

        this.pruefer1Id = pruefer1Id;
        this.pruefer2Id = pruefer2Id;
        this.unterschrift1 = unterschrift1;
        this.unterschrift2 = unterschrift2;
        this.wrongSignatures=false;

        this.payload = payload;
    }

    public long getId() {
        return id;
    }

    public double getBetrag() {
        return betrag;
    }

    public String getEmpfaenger() {
        return empfaenger;
    }

    public int getPruefer1Id() {
        return pruefer1Id;
    }

    public int getPruefer2Id() {
        return pruefer2Id;
    }

    public byte[] getUnterschrift1() {
        return unterschrift1;
    }

    public byte[] getUnterschrift2() {
        return unterschrift2;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public boolean wasReceivedByMSB() {
        return wasReceivedByMSB;
    }

    public void setWasReceivedToTrue() {
        this.wasReceivedByMSB = true;
    }

    public void setWrongSignatures(boolean wrongSignatures) {
        this.wrongSignatures = wrongSignatures;
    }

    @Override
    public String toString() {
        return "Einzelanordnung{" +
                "id=" + id +
                ", timeStamp='" + timeStamp + '\'' +
                ", betrag=" + betrag +
                ", empfaenger='" + empfaenger + '\'' +
                ", pruefer1Id=" + pruefer1Id +
                ", pruefer2Id=" + pruefer2Id +
                ", unterschrift1=" + Base64.getEncoder().encodeToString(unterschrift1).substring(0,10) +
                ", unterschrift2=" + Base64.getEncoder().encodeToString(unterschrift2).substring(0,10) +
                ", wasReceivedByMSB=" + wasReceivedByMSB +
                '}';
    }
}
