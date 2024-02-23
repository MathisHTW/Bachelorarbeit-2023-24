package de.bachelorarbeit.MicroserviceA.Services;

import de.bachelorarbeit.MicroserviceA.DB1.entities.Einzelanordnung;
import de.bachelorarbeit.MicroserviceA.DB2.entities.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.net.ConnectException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

/**
 * Dient der Erzeugung von unterschriebenen und in der DB gespeicherten Einzelanordnungen
 * sollte anstelle von new Einzelanordnung verwendet werden
 */
@Service
public class EinzelanordnungGenerator {

    private static Set<Einzelanordnung> unsignedEinzelanordnung = new HashSet<>();
    private static Set<Einzelanordnung> unsavedEinzelanordnung = new HashSet<>();


    private UserRepositoryService userRepositoryService;

    private EinzelanordnungRepositoryService einzelanordnungRepositoryService;

    @Autowired
    public EinzelanordnungGenerator(UserRepositoryService userRepositoryService, EinzelanordnungRepositoryService einzelanordnungRepositoryService) {
        this.userRepositoryService = userRepositoryService;
        this.einzelanordnungRepositoryService = einzelanordnungRepositoryService;
    }

    Logger logger = LoggerFactory.getLogger(EinzelanordnungGenerator.class);

    /**
     * Generates a Anordnung signs it and saves it in DB1
     * @param betrag
     * @param empfaenger
     * @param pruefer1Id
     * @param pruefer2Id
     * @return the signed and saved (in DB1) einzelanordnung, null if sth pruefer Id does not exist in DB2 or if DB1 or DB 2 are down
     */
    public Einzelanordnung generateSignedEinzelanordnung(double betrag, String empfaenger, int pruefer1Id, int pruefer2Id) {

        //1. Daten für die einzelanordnung zusammentragen:
        //- Betrag
        //- Empfänger
        //- prueferid1 prueferid2
        //- timestamp
        //- unterschrift1 unterschrift2

        //timestamp
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = currentDateTime.format(formatter);

        Random random = new Random();
        byte[] payload = new byte[16000];
        random.nextBytes(payload);

        //unterschriften

        User pruefer1;
        User pruefer2;

        try{
            pruefer1 = userRepositoryService.findById(pruefer1Id);
            pruefer2 = userRepositoryService.findById(pruefer2Id);
        }catch (ConnectException exc){
            logger.debug("ERROR sth went wrong with DB2");
            logger.debug("The message will be signed as soon as DB2 can be reached again.");
            unsignedEinzelanordnung.add(new Einzelanordnung(betrag,empfaenger,timestamp,pruefer1Id,pruefer2Id,null,null,payload));
            return null;
        }


        //wenn die ids nicht in db2 existieren:
        if(pruefer1 == null) {
            logger.debug("ERROR user could not be found in DB2 for given id: " + pruefer1Id);
            logger.debug("Generation of einzelanordnung canceled");
            return null;
        }if(pruefer2 == null) {
            logger.debug("ERROR user could not be found in DB2 for given id: " + pruefer2Id);
            logger.debug("Generation of einzelanordnung canceled");
            return null;
        }


        byte[] unterschrift1 = pruefer1.sign(betrag,empfaenger,timestamp,pruefer1Id,payload);
        byte[] unterschrift2 = pruefer2.sign(betrag,empfaenger,timestamp,pruefer2Id,payload);

        //2. unterschriebenes Einzelanordnungsobjekt erzeugen
        Einzelanordnung einzelanordnung = new Einzelanordnung(betrag,empfaenger,timestamp,pruefer1Id,pruefer2Id,unterschrift1,unterschrift2,payload);

        //3. unterschriebenes objekt in der DB speichern
        Einzelanordnung savedEinzelanordnung = einzelanordnungRepositoryService.save(einzelanordnung);
        if(savedEinzelanordnung == null){//In dem Fall, dass die DB1 nicht erreichbar ist
            logger.debug("Einzelanordnung could not be saved in DB1");
            logger.debug("The message will be saved as soon as DB1 can be reached again.");
            unsavedEinzelanordnung.add(einzelanordnung);
        }
        //4. gespeichertes Objekt das nun über eine ID verfügt zurückgeben
        return savedEinzelanordnung;
    }

    public void signUnsigned() {
        logger.debug("Signing stuck unsigned Einzelanordnungen");
        Iterator<Einzelanordnung> iterator = unsignedEinzelanordnung.iterator();
        while (iterator.hasNext()) {
            Einzelanordnung unsigned = iterator.next();

            double betrag = unsigned.getBetrag();
            String empfaenger = unsigned.getEmpfaenger();
            String timestamp = unsigned.getTimeStamp();
            byte[] payload = unsigned.getPayload();

            int pruefer1Id = unsigned.getPruefer1Id();
            int pruefer2Id = unsigned.getPruefer2Id();

            User pruefer1;
            User pruefer2;

            try {
                pruefer1 = userRepositoryService.findById(pruefer1Id);
                pruefer2 = userRepositoryService.findById(pruefer2Id);
            } catch (ConnectException exc) {
                logger.debug("ERROR sth went wrong with DB2");
                logger.debug("The message will be signed as soon as DB2 can be reached again.");
                return;
            }

            //wenn die ids nicht in db2 existieren:
            if (pruefer1 == null) {
                logger.debug("ERROR user could not be found in DB2 for given id: " + pruefer1Id);
                logger.debug("Generation of einzelanordnung canceled");
                iterator.remove();
                return;
            }
            if (pruefer2 == null) {
                logger.debug("ERROR user could not be found in DB2 for given id: " + pruefer2Id);
                logger.debug("Generation of einzelanordnung canceled");
                iterator.remove();
                return;
            }

            byte[] unterschrift1 = pruefer1.sign(betrag, empfaenger, timestamp, pruefer1Id,payload);
            byte[] unterschrift2 = pruefer2.sign(betrag, empfaenger, timestamp, pruefer2Id,payload);

            //2. Einzelanordnungsobjekt erzeugen
            Einzelanordnung signed = new Einzelanordnung(betrag, empfaenger, timestamp, pruefer1Id, pruefer2Id, unterschrift1, unterschrift2,payload);

            iterator.remove();

            unsavedEinzelanordnung.add(signed);

        }
    }

    public void saveUnsaved(){
        logger.debug("Saving stuck signed Einzelanordnungen");

        Iterator<Einzelanordnung> iterator = unsavedEinzelanordnung.iterator();

        while(iterator.hasNext()){
            Einzelanordnung unsaved = iterator.next();

            Einzelanordnung savedEinzelanordnung = einzelanordnungRepositoryService.save(unsaved);
            if(savedEinzelanordnung == null){//In dem Fall, dass die DB1 nicht erreichbar ist
                logger.debug("Einzelanordnung could not be saved in DB1");
                return;
            }

            iterator.remove();

        }

    }

}
