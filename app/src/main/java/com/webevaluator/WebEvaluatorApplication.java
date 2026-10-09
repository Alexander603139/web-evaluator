package com.webevaluator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WebEvaluatorApplication {
	public static void main(String[] args) {
		SpringApplication.run(WebEvaluatorApplication.class, args);
	}
}