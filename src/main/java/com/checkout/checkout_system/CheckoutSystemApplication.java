package com.checkout.checkout_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CheckoutSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(CheckoutSystemApplication.class, args);
	}

}
