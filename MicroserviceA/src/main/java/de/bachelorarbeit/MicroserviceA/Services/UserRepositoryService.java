package de.bachelorarbeit.MicroserviceA.Services;

import de.bachelorarbeit.MicroserviceA.DB2.entities.User;

import java.net.ConnectException;
import java.util.List;


public interface UserRepositoryService {

    public User save(User user) throws ConnectException;

    public User findById(int id) throws ConnectException;

    public List<User> findAll() throws ConnectException;

    public void deleteById(int id);

}
