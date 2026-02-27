package com.nptechon.smartamp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class SmartampApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartampApplication.class, args);
	}

}
