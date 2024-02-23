package de.bachelorarbeit.MicroserviceB.DB1.repositories;

import de.bachelorarbeit.MicroserviceB.DB1.entities.Einzelanordnung;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EinzelanordnungRepository extends JpaRepository<Einzelanordnung,Long> {
}
