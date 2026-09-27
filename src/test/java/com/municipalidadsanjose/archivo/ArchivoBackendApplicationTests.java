package com.municipalidadsanjose.archivo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.ocr.enabled=false")
class ArchivoBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
