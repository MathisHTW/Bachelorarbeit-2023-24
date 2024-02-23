package de.bachelorarbeit.MicroserviceB.Services;

import java.net.ConnectException;
import java.util.List;
import de.bachelorarbeit.MicroserviceB.DB1.entities.Einzelanordnung;

public interface EinzelanordnungRepositoryService {

    public Einzelanordnung save(Einzelanordnung einzelanordnung) throws ConnectException;

    public Einzelanordnung findById(long id) throws ConnectException;

    public List<Einzelanordnung> findAll() throws ConnectException;


}
