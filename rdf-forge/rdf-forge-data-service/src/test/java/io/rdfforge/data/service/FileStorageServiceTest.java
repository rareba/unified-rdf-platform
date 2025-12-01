package io.rdfforge.data.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @Test
    void testStorageService() {
        FileStorageService service = new FileStorageService();
        assertNotNull(service);
    }
}
