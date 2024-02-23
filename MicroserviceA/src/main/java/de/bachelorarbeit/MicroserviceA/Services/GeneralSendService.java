package de.bachelorarbeit.MicroserviceA.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class GeneralSendService {

    //Wird benötigt um Nachrichten zu Senden und zu Empfangen
    RestTemplate restTemplate;

    @Autowired
    public GeneralSendService(@Qualifier("keyEstablishmentResttemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    Logger logger = LoggerFactory.getLogger(GeneralSendService.class);

    /**
     * @param encryptedMessage die versendet werden soll
     * @param destinationPath die komplette URL an die die nachricht versendet werden soll
     * @return null wenn eine Verbindungn nicht aufgebaut werde konnte
     */
    public ResponseEntity<byte[]> sendPut(byte[] encryptedMessage, String destinationPath){

        //todo test ob die nachricht verschlüsselt wurde
        //  abbruch wenn das nicht der fall ist

        //setting http headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/octet-stream");

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(encryptedMessage, headers);

        ResponseEntity<byte[]> response;
        HttpStatusCode responseStatusCode;
        byte[] responseBody;
        try{
            return restTemplate.exchange(destinationPath, HttpMethod.PUT, requestEntity, byte[].class);
        }catch (ResourceAccessException resexc){ //Wenn MSB nicht erreichbar ist wird abgebrochen
            logger.error("ERROR Connection refused. Keine Verbindung zu MSB. Senden wird abgebrochen.");
            return null;
        }
        catch (HttpStatusCodeException statusCodeException){ //Wenn kein 200er Code zurückkommt sondern z.b. 400
            responseStatusCode =statusCodeException.getStatusCode();
            responseBody = statusCodeException.getResponseBodyAsByteArray();
            return new ResponseEntity<>(responseBody, responseStatusCode);
        }

    }

    //Wird für das Testen  verwendet
    public ResponseEntity<byte[]> sendTestGet(String destinationPath){

        //setting http headers
        HttpHeaders headers = new HttpHeaders();

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<byte[]> response;
        HttpStatusCode responseStatusCode;
        byte[] responseBody;
        try{
            return restTemplate.exchange(destinationPath, HttpMethod.GET, requestEntity, byte[].class);
        }catch (ResourceAccessException resexc){ //Wenn MSB nicht erreichbar ist wird abgebrochen
            logger.error("ERROR Connection refused. Keine Verbindung zu MSB. Senden wird abgebrochen.");
            return null;
        }
        catch (HttpStatusCodeException statusCodeException){ //Wenn kein 200er Code zurückkommt sondern z.b. 400
            responseStatusCode =statusCodeException.getStatusCode();
            responseBody = statusCodeException.getResponseBodyAsByteArray();
            return new ResponseEntity<>(responseBody, responseStatusCode);
        }
    }

}
