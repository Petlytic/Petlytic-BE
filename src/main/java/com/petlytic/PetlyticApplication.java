package com.petlytic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PetlyticApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetlyticApplication.class, args);
    }

}
