package br.com.joaovictornascimento.lead_system.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.joaovictornascimento.lead_system.config.TestcontainersConfiguration;
import br.com.joaovictornascimento.lead_system.domain.CaptureMethod;
import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.Lead;
import br.com.joaovictornascimento.lead_system.domain.LeadOrigin;
import br.com.joaovictornascimento.lead_system.domain.LeadOriginRepository;
import br.com.joaovictornascimento.lead_system.domain.LeadRepository;
import br.com.joaovictornascimento.lead_system.domain.LeadType;
import br.com.joaovictornascimento.lead_system.domain.Role;
import br.com.joaovictornascimento.lead_system.domain.RoleRepository;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import br.com.joaovictornascimento.lead_system.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InteractionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private LeadRepository leadRepository;

	@Autowired
	private LeadOriginRepository leadOriginRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtService jwtService;

	private String tokenFor(User user) {
		return "Bearer " + jwtService.generateToken(user.getId());
	}

	private User createUser(String name, String email, Role role) {
		return userRepository.save(new User(name, email, passwordEncoder.encode("somepassword1"), role, null));
	}

	private Lead createLead(User assignedUser) {
		LeadOrigin origin = leadOriginRepository
			.save(new LeadOrigin(LeadType.DIRECT_CONTACT, Channel.META_WHATSAPP, null, null, null, CaptureMethod.MANUAL));
		return leadRepository
			.save(new Lead("Lead existente", LeadType.DIRECT_CONTACT, null, null, null, null, null, null, origin,
					assignedUser, null));
	}

	private static String interactionJson(String type, String content) {
		return """
				{"type":"%s","content":"%s"}
				""".formatted(type, content);
	}

	@Test
	void createInteractionWithoutTokenReturnsUnauthorized() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-interactions1@leadsystem.local", salesperson);
		Lead anaLead = createLead(ana);

		mockMvc
			.perform(post("/api/leads/" + anaLead.getId() + "/interactions").contentType("application/json")
				.content(interactionJson("NOTE", "Ligou e não atendeu")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createInteractionWithBlankContentReturnsBadRequest() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-interactions2@leadsystem.local", salesperson);
		Lead anaLead = createLead(ana);

		mockMvc
			.perform(post("/api/leads/" + anaLead.getId() + "/interactions").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content(interactionJson("NOTE", "")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createInteractionOnOwnLeadReturnsCreated() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-interactions3@leadsystem.local", salesperson);
		Lead anaLead = createLead(ana);

		mockMvc
			.perform(post("/api/leads/" + anaLead.getId() + "/interactions").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content(interactionJson("CALL", "Ligou e agendou reunião")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.leadId").value(anaLead.getId().toString()))
			.andExpect(jsonPath("$.userId").value(ana.getId().toString()))
			.andExpect(jsonPath("$.type").value("CALL"))
			.andExpect(jsonPath("$.content").value("Ligou e agendou reunião"));
	}

	@Test
	void createInteractionOnAnotherUsersLeadWithViewOwnLeadsOnlyReturnsForbidden() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-interactions4@leadsystem.local", salesperson);
		User bruno = createUser("Bruno", "bruno-interactions1@leadsystem.local", salesperson);
		Lead brunoLead = createLead(bruno);

		mockMvc
			.perform(post("/api/leads/" + brunoLead.getId() + "/interactions").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content(interactionJson("NOTE", "Tentativa de contato")))
			.andExpect(status().isForbidden());
	}

	@Test
	void createInteractionOnUnknownLeadReturnsNotFound() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-interactions1@leadsystem.local", manager);

		mockMvc
			.perform(post("/api/leads/" + UUID.randomUUID() + "/interactions").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(admin))
				.content(interactionJson("NOTE", "Observação")))
			.andExpect(status().isNotFound());
	}

	@Test
	void listInteractionsWithoutTokenReturnsUnauthorized() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-interactions5@leadsystem.local", salesperson);
		Lead anaLead = createLead(ana);

		mockMvc.perform(get("/api/leads/" + anaLead.getId() + "/interactions")).andExpect(status().isUnauthorized());
	}

	@Test
	void listInteractionsOnOwnLeadReturnsChronologicalOrder() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-interactions6@leadsystem.local", salesperson);
		Lead anaLead = createLead(ana);

		mockMvc
			.perform(post("/api/leads/" + anaLead.getId() + "/interactions").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content(interactionJson("NOTE", "Primeira observação")))
			.andExpect(status().isCreated());

		mockMvc
			.perform(post("/api/leads/" + anaLead.getId() + "/interactions").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content(interactionJson("CALL", "Segunda interação")))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/leads/" + anaLead.getId() + "/interactions").header(HttpHeaders.AUTHORIZATION, tokenFor(ana)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].content").value("Primeira observação"))
			.andExpect(jsonPath("$[1].content").value("Segunda interação"));
	}

	@Test
	void listInteractionsOnAnotherUsersLeadWithViewOwnLeadsOnlyReturnsForbidden() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-interactions7@leadsystem.local", salesperson);
		User bruno = createUser("Bruno", "bruno-interactions2@leadsystem.local", salesperson);
		Lead brunoLead = createLead(bruno);

		mockMvc
			.perform(get("/api/leads/" + brunoLead.getId() + "/interactions").header(HttpHeaders.AUTHORIZATION, tokenFor(ana)))
			.andExpect(status().isForbidden());
	}

	@Test
	void listInteractionsWithViewAllLeadsSeesAnyLeadsInteractions() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-interactions2@leadsystem.local", manager);
		User bruno = createUser("Bruno", "bruno-interactions3@leadsystem.local", salesperson);
		Lead brunoLead = createLead(bruno);

		mockMvc
			.perform(post("/api/leads/" + brunoLead.getId() + "/interactions").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(admin))
				.content(interactionJson("NOTE", "Observação do gestor")))
			.andExpect(status().isCreated());

		mockMvc
			.perform(get("/api/leads/" + brunoLead.getId() + "/interactions").header(HttpHeaders.AUTHORIZATION, tokenFor(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void listInteractionsOnUnknownLeadReturnsNotFound() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-interactions3@leadsystem.local", manager);

		mockMvc
			.perform(get("/api/leads/" + UUID.randomUUID() + "/interactions").header(HttpHeaders.AUTHORIZATION, tokenFor(admin)))
			.andExpect(status().isNotFound());
	}

}
