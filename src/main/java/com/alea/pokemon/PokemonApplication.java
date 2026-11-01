package com.alea.pokemon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point: boots Spring, scans {@code @ConfigurationProperties} records and enables scheduled jobs. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PokemonApplication {

    /** Starts the application. */
    public static void main(String[] args) {
        SpringApplication.run(PokemonApplication.class, args);
    }
}
