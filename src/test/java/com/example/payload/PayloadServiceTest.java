package com.example.payload;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class PayloadServiceTest {

    @Autowired
    private PayloadService payloadService;

    @Test
    public void testMultiplePayloadsProcessing() throws InterruptedException {
        for (int p = 1; p <= 3; p++) {
            List<Record> records = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Record r = new Record();
                r.key = "key" + (i % 10);  // 10 different keys
                r.value = "value" + i;
                records.add(r);
            }
            payloadService.submitLargePayload("payload-" + p, records);
        }

        // Wait for all processing to complete
        Thread.sleep(5000);
    }
}