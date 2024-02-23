package de.bachelorarbeit.MicroserviceA.DB2.repositories;

import de.bachelorarbeit.MicroserviceA.DB2.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
}
