package br.com.joaovictornascimento.lead_system.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import br.com.joaovictornascimento.lead_system.domain.Product;
import br.com.joaovictornascimento.lead_system.domain.ProductRepository;
import br.com.joaovictornascimento.lead_system.domain.ProductType;
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
class LeadControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private ProductRepository productRepository;

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

	private static String directContactLeadJson(String name, UUID assignedUserId) {
		return """
				{"name":"%s","leadType":"DIRECT_CONTACT","assignedUserId":"%s","channel":"META_WHATSAPP"}
				""".formatted(name, assignedUserId);
	}

	private static String localSearchLeadJson(String name, UUID assignedUserId) {
		return """
				{"name":"%s","leadType":"LOCAL_SEARCH","assignedUserId":"%s","searchSource":"GOOGLE_MAPS","region":"São Paulo","searchSegment":"Restaurantes"}
				""".formatted(name, assignedUserId);
	}

	private static String updateLeadJson(String name, UUID assignedUserId) {
		return """
				{"name":"%s","assignedUserId":"%s"}
				""".formatted(name, assignedUserId);
	}

	@Test
	void createLeadWithoutTokenReturnsUnauthorized() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User salespersonUser = createUser("Vendedor", "vendedor-leads1@leadsystem.local", salesperson);

		mockMvc
			.perform(post("/api/leads").contentType("application/json")
				.content(directContactLeadJson("Novo Lead", salespersonUser.getId())))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createDirectContactLeadWithChannelReturnsCreated() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User salespersonUser = createUser("Vendedor", "vendedor-leads2@leadsystem.local", salesperson);

		mockMvc
			.perform(post("/api/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(salespersonUser))
				.content(directContactLeadJson("Novo Lead", salespersonUser.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Novo Lead"))
			.andExpect(jsonPath("$.funnelStatus").value("NEW"))
			.andExpect(jsonPath("$.origin.captureMethod").value("MANUAL"))
			.andExpect(jsonPath("$.origin.channel").value("META_WHATSAPP"))
			.andExpect(jsonPath("$.statusHistory.length()").value(1))
			.andExpect(jsonPath("$.statusHistory[0].previousStatus").doesNotExist())
			.andExpect(jsonPath("$.statusHistory[0].newStatus").value("NEW"));
	}

	@Test
	void createDirectContactLeadWithoutChannelReturnsBadRequest() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User salespersonUser = createUser("Vendedor", "vendedor-leads3@leadsystem.local", salesperson);

		mockMvc
			.perform(post("/api/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(salespersonUser))
				.content("""
						{"name":"Novo Lead","leadType":"DIRECT_CONTACT","assignedUserId":"%s"}
						""".formatted(salespersonUser.getId())))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createLocalSearchLeadMissingSearchFieldsReturnsBadRequest() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User salespersonUser = createUser("Vendedor", "vendedor-leads4@leadsystem.local", salesperson);

		mockMvc
			.perform(post("/api/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(salespersonUser))
				.content("""
						{"name":"Novo Lead","leadType":"LOCAL_SEARCH","assignedUserId":"%s"}
						""".formatted(salespersonUser.getId())))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createLeadWithUnknownAssignedUserReturnsNotFound() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User salespersonUser = createUser("Vendedor", "vendedor-leads5@leadsystem.local", salesperson);

		mockMvc
			.perform(post("/api/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(salespersonUser))
				.content(directContactLeadJson("Novo Lead", UUID.randomUUID())))
			.andExpect(status().isNotFound());
	}

	@Test
	void createLeadWithUnknownProductReturnsNotFound() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User salespersonUser = createUser("Vendedor", "vendedor-leads6@leadsystem.local", salesperson);

		mockMvc
			.perform(post("/api/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(salespersonUser))
				.content("""
						{"name":"Novo Lead","leadType":"DIRECT_CONTACT","assignedUserId":"%s","channel":"TELEGRAM","productIds":["%s"]}
						""".formatted(salespersonUser.getId(), UUID.randomUUID())))
			.andExpect(status().isNotFound());
	}

	@Test
	void createLocalSearchLeadWithAllSearchFieldsReturnsCreated() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-leads1@leadsystem.local", manager);
		Product product = productRepository
			.save(new Product("Site institucional", ProductType.READY_MADE, "desc", 50000, 100000));

		mockMvc
			.perform(post("/api/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(admin))
				.content("""
						{"name":"Empresa Local","leadType":"LOCAL_SEARCH","assignedUserId":"%s","searchSource":"GOOGLE_MAPS","region":"São Paulo","searchSegment":"Restaurantes","productIds":["%s"]}
						""".formatted(admin.getId(), product.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.origin.searchSource").value("GOOGLE_MAPS"))
			.andExpect(jsonPath("$.productsOfInterest[0].name").value("Site institucional"));
	}

	@Test
	void createLeadWithStrongCriteriaReturnsHighQualification() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-leads-qualification1@leadsystem.local", salesperson);
		Product product = productRepository
			.save(new Product("Site institucional", ProductType.READY_MADE, "desc", 50000, 100000));

		mockMvc
			.perform(post("/api/leads").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content("""
						{"name":"Lead Quente","leadType":"DIRECT_CONTACT","assignedUserId":"%s","channel":"TELEGRAM","estimatedBudgetCents":500000,"desiredTimeline":"imediato","productIds":["%s"]}
						""".formatted(ana.getId(), product.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.qualificationScore").value("HIGH"));
	}

	@Test
	void listLeadsWithoutTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/leads")).andExpect(status().isUnauthorized());
	}

	@Test
	void listLeadsWithViewOwnLeadsOnlyReturnsOnlyOwnLeadsEvenWhenFilteringByOtherUser() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-leads1@leadsystem.local", salesperson);
		User bruno = createUser("Bruno", "bruno-leads1@leadsystem.local", salesperson);
		createLead(ana);
		createLead(bruno);

		mockMvc.perform(get("/api/leads").param("assignedUserId", bruno.getId().toString())
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].assignedUserId").value(ana.getId().toString()));
	}

	@Test
	void listLeadsWithViewAllLeadsReturnsEveryoneAndRespectsFilters() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-leads2@leadsystem.local", manager);
		User ana = createUser("Ana", "ana-leads2@leadsystem.local", salesperson);
		User bruno = createUser("Bruno", "bruno-leads2@leadsystem.local", salesperson);
		createLead(ana);
		createLead(bruno);

		mockMvc.perform(get("/api/leads").header(HttpHeaders.AUTHORIZATION, tokenFor(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.assignedUserId=='" + ana.getId() + "')]").exists())
			.andExpect(jsonPath("$[?(@.assignedUserId=='" + bruno.getId() + "')]").exists());

		mockMvc.perform(get("/api/leads").param("assignedUserId", ana.getId().toString())
				.header(HttpHeaders.AUTHORIZATION, tokenFor(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].assignedUserId").value(ana.getId().toString()));
	}

	@Test
	void getLeadWithViewOwnLeadsOnlyAccessingAnotherUsersLeadReturnsForbidden() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-leads3@leadsystem.local", salesperson);
		User bruno = createUser("Bruno", "bruno-leads3@leadsystem.local", salesperson);
		Lead brunoLead = createLead(bruno);

		mockMvc.perform(get("/api/leads/" + brunoLead.getId()).header(HttpHeaders.AUTHORIZATION, tokenFor(ana)))
			.andExpect(status().isForbidden());
	}

	@Test
	void getLeadWithViewAllLeadsAccessesAnyLead() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-leads3@leadsystem.local", manager);
		User bruno = createUser("Bruno", "bruno-leads4@leadsystem.local", salesperson);
		Lead brunoLead = createLead(bruno);

		mockMvc.perform(get("/api/leads/" + brunoLead.getId()).header(HttpHeaders.AUTHORIZATION, tokenFor(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(brunoLead.getId().toString()));
	}

	@Test
	void getUnknownLeadReturnsNotFound() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-leads4@leadsystem.local", manager);

		mockMvc
			.perform(get("/api/leads/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, tokenFor(admin)))
			.andExpect(status().isNotFound());
	}

	@Test
	void updateOwnLeadReassignsAndUpdatesQualification() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-leads5@leadsystem.local", salesperson);
		User bruno = createUser("Bruno", "bruno-leads5@leadsystem.local", salesperson);
		Lead anaLead = createLead(ana);

		mockMvc
			.perform(put("/api/leads/" + anaLead.getId()).contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content(updateLeadJson("Lead Atualizado", bruno.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Lead Atualizado"))
			.andExpect(jsonPath("$.assignedUserId").value(bruno.getId().toString()));
	}

	@Test
	void updateLeadWithStrongCriteriaRecalculatesQualification() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-leads-qualification2@leadsystem.local", salesperson);
		Lead anaLead = createLead(ana);
		Product product = productRepository
			.save(new Product("Sistema sob medida", ProductType.CUSTOM, "desc", null, null));

		mockMvc
			.perform(put("/api/leads/" + anaLead.getId()).contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content("""
						{"name":"Lead Atualizado","assignedUserId":"%s","estimatedBudgetCents":500000,"desiredTimeline":"urgente","productIds":["%s"]}
						""".formatted(ana.getId(), product.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.qualificationScore").value("HIGH"));
	}

	@Test
	void updateAnotherUsersLeadWithViewOwnLeadsOnlyReturnsForbidden() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-leads6@leadsystem.local", salesperson);
		User bruno = createUser("Bruno", "bruno-leads6@leadsystem.local", salesperson);
		Lead brunoLead = createLead(bruno);

		mockMvc
			.perform(put("/api/leads/" + brunoLead.getId()).contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content(updateLeadJson("Lead Atualizado", ana.getId())))
			.andExpect(status().isForbidden());
	}

	@Test
	void updateUnknownLeadReturnsNotFound() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-leads5@leadsystem.local", manager);

		mockMvc
			.perform(put("/api/leads/" + UUID.randomUUID()).contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(admin))
				.content(updateLeadJson("Lead Atualizado", admin.getId())))
			.andExpect(status().isNotFound());
	}

	@Test
	void updateStatusWithoutTokenReturnsUnauthorized() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-leads7@leadsystem.local", salesperson);
		Lead anaLead = createLead(ana);

		mockMvc
			.perform(patch("/api/leads/" + anaLead.getId() + "/status").contentType("application/json")
				.content("""
						{"newStatus":"CONTACTED"}
						"""))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void updateStatusToLostWithoutReasonReturnsBadRequest() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-leads8@leadsystem.local", salesperson);
		Lead anaLead = createLead(ana);

		mockMvc
			.perform(patch("/api/leads/" + anaLead.getId() + "/status").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content("""
						{"newStatus":"LOST"}
						"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void updateStatusToLostWithReasonUpdatesFunnelStatusAndLossReason() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-leads9@leadsystem.local", salesperson);
		Lead anaLead = createLead(ana);

		mockMvc
			.perform(patch("/api/leads/" + anaLead.getId() + "/status").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content("""
						{"newStatus":"LOST","reason":"Cliente não respondeu"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.funnelStatus").value("LOST"))
			.andExpect(jsonPath("$.lossReason").value("Cliente não respondeu"))
			.andExpect(jsonPath("$.statusHistory[0].previousStatus").value("NEW"))
			.andExpect(jsonPath("$.statusHistory[0].newStatus").value("LOST"))
			.andExpect(jsonPath("$.statusHistory[0].reason").value("Cliente não respondeu"));
	}

	@Test
	void updateStatusToCommonTransitionDoesNotRequireReason() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-leads10@leadsystem.local", salesperson);
		Lead anaLead = createLead(ana);

		mockMvc
			.perform(patch("/api/leads/" + anaLead.getId() + "/status").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content("""
						{"newStatus":"CONTACTED"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.funnelStatus").value("CONTACTED"))
			.andExpect(jsonPath("$.lossReason").doesNotExist());
	}

	@Test
	void updateStatusWithViewOwnLeadsOnlyOnAnotherUsersLeadReturnsForbidden() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User ana = createUser("Ana", "ana-leads11@leadsystem.local", salesperson);
		User bruno = createUser("Bruno", "bruno-leads7@leadsystem.local", salesperson);
		Lead brunoLead = createLead(bruno);

		mockMvc
			.perform(patch("/api/leads/" + brunoLead.getId() + "/status").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(ana))
				.content("""
						{"newStatus":"CONTACTED"}
						"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void updateStatusOfUnknownLeadReturnsNotFound() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-leads6@leadsystem.local", manager);

		mockMvc
			.perform(patch("/api/leads/" + UUID.randomUUID() + "/status").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(admin))
				.content("""
						{"newStatus":"CONTACTED"}
						"""))
			.andExpect(status().isNotFound());
	}

}
