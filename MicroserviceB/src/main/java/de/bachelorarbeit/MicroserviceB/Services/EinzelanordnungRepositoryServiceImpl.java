package de.bachelorarbeit.MicroserviceB.Services;

import de.bachelorarbeit.MicroserviceB.DB1.entities.Einzelanordnung;
import de.bachelorarbeit.MicroserviceB.DB1.repositories.EinzelanordnungRepository;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.util.List;
import java.util.Optional;

@Service
public class EinzelanordnungRepositoryServiceImpl implements EinzelanordnungRepositoryService{
    private EinzelanordnungRepository einzelanordnungRepository;

    Logger logger = LoggerFactory.getLogger(EinzelanordnungRepositoryServiceImpl.class);

    @Autowired
    public EinzelanordnungRepositoryServiceImpl(EinzelanordnungRepository einzelanordnungRepository) {
        this.einzelanordnungRepository = einzelanordnungRepository;
    }

    /**
     * Speichert die Einzelanordnung in der DB1 wenn die ID nicht schon existiert
     * @param einzelanordnung
     * @return null wenn die ID schon existiert, die gespeicherte einzelanordnung wenn alles funktioniert
     * @throws ConnectException if the DB1 cannot be reached
     */
    @Override
    public Einzelanordnung save(Einzelanordnung einzelanordnung) throws ConnectException {
        Einzelanordnung foundEinzelanordnung = this.findById(einzelanordnung.getId());
        if(foundEinzelanordnung != null) {
            logger.warn("WARN die ID existiert schon in der DB1. Die einzelanordnung wird nicht gespeichert.");
            return null;
        }

        try {
            return einzelanordnungRepository.save(einzelanordnung);
        }catch (ConstraintViolationException | DataIntegrityViolationException exc){
            logger.warn("WARN die ID existiert schon in der DB1. Die einzelanordnung wird nicht gespeichert.");
            return null;
        } catch (Exception exc){
            logger.error("ERROR STH WRONG WITH DB1");
            throw new ConnectException();
        }
    }

    /**
     *
     * @param id
     * @return null if einzelanordnung was not found in the db
     * @throws ConnectException if sth is wrong with DB1
     */
    @Override
    public Einzelanordnung findById(long id) throws ConnectException {
        Optional<Einzelanordnung> einzelanordnungOptional;
        try{
            einzelanordnungOptional = einzelanordnungRepository.findById(id);
        }catch(Exception exc){
            logger.error("ERROR STH WRONG WITH DB1");
            throw new ConnectException();
        }

        if(einzelanordnungOptional.isPresent()){
            return einzelanordnungOptional.get();
        }
        return null;
    }

    /**
     *
     * @return Liste aller einzelanordnungen in DB1, leere liste, wenn keine Elemente gefunden, null wenn etwas mit DB1 nicht stimmt
     * @throws ConnectException
     */
    @Override
    public List<Einzelanordnung> findAll() throws ConnectException {
        try{
            return einzelanordnungRepository.findAll();
        }catch (Exception exc){
            logger.error("ERROR DB1 could not be reached");
            throw new ConnectException();
        }
    }
}
