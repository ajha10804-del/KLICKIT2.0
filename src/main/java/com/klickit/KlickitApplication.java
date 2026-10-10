package com.klickit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KlickitApplication {

    public static void main(String[] args) {
        SpringApplication.run(KlickitApplication.class, args);
    }
}
