package com.example.payload;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test configuration that scans both bhpubwrt and bhwrtam packages for integration testing.
 */
@SpringBootApplication(scanBasePackages = {
    "com.example.payload"  // Scan all packages under com.example.payload
})
public class TestApplication {
    // Integration test application that includes both modules
}

