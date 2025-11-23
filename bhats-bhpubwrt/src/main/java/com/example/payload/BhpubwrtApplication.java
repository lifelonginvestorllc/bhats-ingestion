package com.example.payload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.payload.bhpubwrt", "com.example.payload.common"})
public class BhpubwrtApplication {
    public static void main(String[] args) {
        SpringApplication.run(BhpubwrtApplication.class, args);
    }
}
