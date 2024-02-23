package de.bachelorarbeit.MicroserviceB.DB2.repositories;

import de.bachelorarbeit.MicroserviceB.DB2.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer>{
}
