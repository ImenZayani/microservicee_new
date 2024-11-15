package tn.esprit.order_service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderServiceApplicationTests {

	@Test
	void contextLoads() {
		Assumptions.assumeTrue(
				System.getenv("DOCKER_ENABLED") != null,
				"Docker non configuré, test ignoré"
		);

	}
}
