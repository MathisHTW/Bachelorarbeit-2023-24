package de.bachelorarbeit.MicroserviceA.Services;

import de.bachelorarbeit.MicroserviceA.DB2.repositories.UserRepository;
import de.bachelorarbeit.MicroserviceA.DB2.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.util.List;
import java.util.Optional;

@Service
public class UserRepositoryServiceImpl implements UserRepositoryService{


    private UserRepository userRepository;

    @Autowired
    public UserRepositoryServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    Logger logger = LoggerFactory.getLogger(UserRepositoryServiceImpl.class);

    @Override
    public User save(User user) throws ConnectException{
        try{
            return userRepository.save(user);
        }catch (Exception exc){
            logger.error("ERROR STH WRONG WITH DB2");
            throw new ConnectException();
        }
    }

    /**
     *
     * @param id
     * @return null if not found in db, throws ConnectException if anything went wrong
     */
    @Override
    public User findById(int id) throws ConnectException{

        try{
            Optional<User> userOpt = userRepository.findById(id);
            if(userOpt.isPresent()){
                return userOpt.get();
            }
            logger.error("ERROR user with id: " + id + " could not be found in DB2");
            return null;
        }catch(Exception exc){//falls DB2 nicht erreichbar ist
            logger.error("ERROR STH WRONG WITH DB2");
            throw new ConnectException();
        }

    }

    /**
     * @return List of all found users in DB2, liefert eine leere liste, wenn keine user gefunden wurden
     * @throws ConnectException if sth is wrong with DB2
     */
    @Override
    public List<User> findAll() throws ConnectException{
        try{
            return userRepository.findAll();
        }catch (Exception exc){
            logger.error("ERROR STH WRONG WITH DB2");
            throw new ConnectException();
        }
    }

    @Override
    public void deleteById(int id) {
        userRepository.deleteById(id);
    }
}
