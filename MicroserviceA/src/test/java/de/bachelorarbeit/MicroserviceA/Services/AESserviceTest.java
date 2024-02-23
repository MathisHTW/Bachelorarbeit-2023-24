package de.bachelorarbeit.MicroserviceA.Services;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AESserviceTest {

    Logger logger = LoggerFactory.getLogger(AESserviceTest.class);


    private String containerNummer = "0";

    //Soll 32bits / 4 byte lang sein
    private String fixedField = "MSA" + containerNummer;

    // soll 96-32bits lang sein, also 64bits also long
    private long invocationField = 0L;


    @Test
    public void testIVLength() {
        // Mocking necessary variables or obtaining them as needed for testing
        long invocationField = 0L;
        String fixedField = "MSA0";

        // Generate IV using the method under test
        byte[] generatedIV = generateIV();

        // Assert the length of the generated IV is 96 bits (12 bytes)
        assertEquals(12, generatedIV.length, "IV length should be 96 bits (12 bytes)");

        String originalString = extractStringFromByteArray(generatedIV);
        long originalLong = extractLongFromByteArray(generatedIV);
        assertEquals(fixedField,originalString);
        assertEquals(invocationField,originalLong);
    }

    public byte[] generateIV(){
        byte[] invocationFieldArray = ByteBuffer.allocate(8).putLong(invocationField).array();
        logger.debug("invocationField = " + invocationFieldArray + " - length = " + invocationFieldArray.length);
        byte[] fixedFieldArray = fixedField.getBytes(StandardCharsets.UTF_8);
        logger.debug("fixedField = " + fixedFieldArray + " - length = " + fixedFieldArray.length);

        byte[] IV = new byte[12];
        System.arraycopy(fixedFieldArray, 0, IV, 0, 4);
        System.arraycopy(invocationFieldArray, 0, IV, 4, 8);

        return IV;

    }

    // Method to extract the String from the byte array
    private static String extractStringFromByteArray(byte[] byteArray) {
        byte[] stringBytes = new byte[4];
        System.arraycopy(byteArray, 0, stringBytes, 0, 4);
        return new String(stringBytes, StandardCharsets.UTF_8);
    }

    // Method to extract the long from the byte array
    private static long extractLongFromByteArray(byte[] byteArray) {
        ByteBuffer buffer = ByteBuffer.wrap(byteArray, 4, 8);
        return buffer.getLong();
    }

}