package br.com.joaovictornascimento.lead_system;

import br.com.joaovictornascimento.lead_system.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LeadSystemApplicationTests {

	@Test
	void contextLoads() {
	}

}
