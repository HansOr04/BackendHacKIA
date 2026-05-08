package com.hackIAThon.solutionback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableCaching
@EnableRetry
public class SolutionBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(SolutionBackApplication.class, args);
    }

}