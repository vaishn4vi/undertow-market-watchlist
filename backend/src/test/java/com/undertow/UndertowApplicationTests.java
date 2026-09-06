package com.undertow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UndertowApplicationTests {

    @Test
    void contextLoads() {
        // Passes if the whole application context (all modules) wires up cleanly.
    }
}
