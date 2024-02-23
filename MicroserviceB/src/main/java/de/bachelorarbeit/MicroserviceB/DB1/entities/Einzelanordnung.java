package de.bachelorarbeit.MicroserviceB.DB1.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;

@Entity
@Table(name="einzelanordnung")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Einzelanordnung {

    @Id
    @Column(unique = true,name="id")//todo handle ConstraintViolationException wenn versucht wird eine Einzelanordnung mit derselben id zu speichern
    long id;

    //todo weitere Daten für den Inhalt der Einzelanordnung
    @Column(name="betrag")
    double betrag;

    @Column(name="empfaenger")
    String empfaenger;

    @Column(name="time-stamp")
    String timeStamp;

    @Column(name="pruefer1-id")
    int pruefer1Id;
    @Column(name="pruefer2-id")
    int pruefer2Id;

    @Column(name="unterschrift-pruefer-1", length = 5000)
    byte[] unterschrift1;

    @Column(name="unterschrift-pruefer-2", length = 5000)
    byte[] unterschrift2;

    @Column(name="payload",length = 16000)
    private byte[] payload;

    public byte[] getPayload() {
        return payload;
    }

    public Einzelanordnung() {
    }

    public Einzelanordnung(double betrag, String empfaenger,String timeStamp, int pruefer1Id, int pruefer2Id, byte[] unterschrift1, byte[] unterschrift2) {
        this.betrag = betrag;
        this.empfaenger = empfaenger;
        this.timeStamp = timeStamp;
        this.pruefer1Id = pruefer1Id;
        this.pruefer2Id = pruefer2Id;
        //todo 1. es werden die ids der prüfer genommen um deren Instanz aus der DB zu holen über deren Instanz kann dann auf die sign methode zugegeriffen werden
        //todo die datei mit dem private keys von prüfer1 und 2 unterschreiben
        this.unterschrift1=unterschrift1;//todo pruefer1Obj.sign(einzelanordnung daten)
        this.unterschrift2=unterschrift2;
    }

    //todo dient nur dem Testen und sollte gelöscht werden
    /**
     * Erzeugt eine TestEinzelanordnung mit der Id 999999
     * @return
     */
    public static Einzelanordnung generateTestEinzelanordnung(){
        Einzelanordnung einzelanordnung = new Einzelanordnung(99999.0,"TEST-EMPFAENGER","TIMESTAMP",1,2,new byte[0],new byte[0]);
        einzelanordnung.id = 999999;
        return einzelanordnung;
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



    @Override
    public String toString() {
        return "Einzelanordnung{" +
                "id=" + id +
                ", betrag=" + betrag +
                ", empfaenger='" + empfaenger + '\'' +
                ", timestamp='" + timeStamp +
                ", pruefer1Id=" + pruefer1Id +
                ", pruefer2Id=" + pruefer2Id +
                ", unterschrift1=" + Arrays.toString(unterschrift1) +
                ", unterschrift2=" + Arrays.toString(unterschrift2) +
                '}';
    }
}
