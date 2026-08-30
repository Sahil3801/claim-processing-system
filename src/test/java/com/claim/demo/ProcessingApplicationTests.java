package com.claim.demo;

import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ProcessingApplicationTests {

	@Autowired
	private Flyway flyway;

	@MockBean
	private JavaMailSender javaMailSender;

	@Test
	void contextLoads() {
		assertEquals("6", flyway.info().current().getVersion().getVersion());
	}

}
