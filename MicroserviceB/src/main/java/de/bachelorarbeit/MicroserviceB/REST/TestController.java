package de.bachelorarbeit.MicroserviceB.REST;


import de.bachelorarbeit.MicroserviceB.Services.AESservice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    AESservice aeSservice;

    @Autowired
    public TestController(AESservice aeSservice) {
        this.aeSservice = aeSservice;
    }

    @GetMapping()
    public ResponseEntity<byte[]> getCurrentSessionKey(){
        byte[] currentSessionKey = aeSservice.getSessionKey().getEncoded();
        return ResponseEntity.status(HttpStatus.OK).body(currentSessionKey);
    }
}
