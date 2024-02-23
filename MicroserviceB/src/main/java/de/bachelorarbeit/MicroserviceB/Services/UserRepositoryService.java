package de.bachelorarbeit.MicroserviceB.Services;

import java.net.ConnectException;
import java.util.List;

import de.bachelorarbeit.MicroserviceB.DB2.entities.User;


public interface UserRepositoryService {

    public User save(User user) throws ConnectException;

    public User findById(int id) throws ConnectException;

    public List<User> findAll() throws ConnectException;

}
