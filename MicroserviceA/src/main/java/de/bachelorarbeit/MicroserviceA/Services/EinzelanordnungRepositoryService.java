package de.bachelorarbeit.MicroserviceA.Services;

import de.bachelorarbeit.MicroserviceA.DB1.entities.Einzelanordnung;

import java.net.ConnectException;
import java.util.List;

public interface EinzelanordnungRepositoryService {

    public Einzelanordnung save(Einzelanordnung einzelanordnung);

    public Einzelanordnung findById(long id) throws ConnectException;

    public List<Einzelanordnung> findAll() throws ConnectException;

    public Einzelanordnung updateReceivedStatus(long id);

    List<Einzelanordnung> findAllNotReceived();

    public boolean flag(long id);
}
