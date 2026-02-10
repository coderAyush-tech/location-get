package com.example.location.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class LocationAppApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(LocationAppApplication.class, args);



		String mongoUri = context.getEnvironment().getProperty("spring.data.mongodb.uri");
		System.out.println("--------------------------------------------------");
		System.out.println("🔥 MY MONGO URI IS: " + mongoUri);
		System.out.println("--------------------------------------------------");

	}


}
