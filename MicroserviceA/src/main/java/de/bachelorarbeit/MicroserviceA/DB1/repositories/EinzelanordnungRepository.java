package de.bachelorarbeit.MicroserviceA.DB1.repositories;

import de.bachelorarbeit.MicroserviceA.DB1.entities.Einzelanordnung;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EinzelanordnungRepository extends JpaRepository<Einzelanordnung,Long> {

    List<Einzelanordnung> findAllByWasReceivedByMSBIsFalse();

}
