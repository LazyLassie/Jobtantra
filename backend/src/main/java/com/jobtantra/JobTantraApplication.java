package com.jobtantra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JobTantraApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobTantraApplication.class, args);
    }
}
