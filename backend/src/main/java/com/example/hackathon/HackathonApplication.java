package com.example.hackathon;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HackathonApplication {

	public static void main(String[] args) {
		// Windows maps India to the legacy id "Asia/Calcutta", which postgres:16 rejects
		// as a session TimeZone, so the JDBC connection fails. Pin the JVM to UTC.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(HackathonApplication.class, args);
	}

}
