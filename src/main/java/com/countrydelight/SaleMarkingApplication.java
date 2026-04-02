package com.countrydelight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.countrydelight"})
public class SaleMarkingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaleMarkingApplication.class, args);
    }
}
