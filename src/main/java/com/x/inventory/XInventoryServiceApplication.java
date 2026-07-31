package com.x.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class XInventoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(XInventoryServiceApplication.class, args);
	}

}
