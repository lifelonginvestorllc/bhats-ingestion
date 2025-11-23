package com.example.payload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.payload.bhwrtam", "com.example.payload.common"})
public class BhwrtamApplication {
    public static void main(String[] args) {
        SpringApplication.run(BhwrtamApplication.class, args);
    }
}
