package de.toengi.cili;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.model.entity.Customer;
import de.toengi.cili.model.entity.User;
import de.toengi.cili.model.enums.UserRole;
import de.toengi.cili.repository.CustomerRepository;
import de.toengi.cili.repository.UserRepository;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired CustomerRepository customerRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private User sponsorA;
    private User sponsorB;
    private User adminUser;
    private String tokenA;
    private String tokenB;
    private String adminToken;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
        userRepository.deleteAll();

        sponsorA = userRepository.save(User.builder()
                .username("sponsorA").email("sponsora@test.com").passwordHash("x").build());
        sponsorB = userRepository.save(User.builder()
                .username("sponsorB").email("sponsorb@test.com").passwordHash("x").build());
        adminUser = userRepository.save(User.builder()
                .username("admin").email("admin@test.com").passwordHash("x").role(UserRole.ADMIN).build());

        tokenA = jwtTokenProvider.generateAccessToken(new CiliUserDetails(sponsorA));
        tokenB = jwtTokenProvider.generateAccessToken(new CiliUserDetails(sponsorB));
        adminToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(adminUser));
    }

    @Test
    void createCustomer_returns201AndPersistsConsent() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateReq("Anna Beispiel", "anna@example.com"));

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consentGranted").value(true))
                .andExpect(jsonPath("$.sponsorUserId").value(sponsorA.getId()));
    }

    @Test
    void createCustomer_invalidEmail_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateReq("Anna", "not-an-email"));

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCustomer_duplicateEmailSameSponsor_returns409() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateReq("Anna", "anna@example.com"));
        mockMvc.perform(post("/api/customers")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON).content(body));

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createCustomer_sameEmailDifferentSponsors_bothSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateReq("Anna", "anna@example.com"));

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createCustomer_withOptionalProfileFields_persistsAndReturnsThem() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateFullReq(
                "Anna Beispiel", "Anna", "anna@example.com", "+49 151 12345678",
                LocalDate.of(1990, 5, 20), 123456, "WEIBLICH", true));

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Anna"))
                .andExpect(jsonPath("$.mobilePhone").value("+49 151 12345678"))
                .andExpect(jsonPath("$.birthDate").value("1990-05-20"))
                .andExpect(jsonPath("$.memberId").value(123456))
                .andExpect(jsonPath("$.gender").value("WEIBLICH"))
                .andExpect(jsonPath("$.informalAddress").value(true));
    }

    @Test
    void createCustomer_memberIdOutOfRange_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateFullReq(
                "Anna", null, "anna2@example.com", null, null, 42, null, null));

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCustomer_foreignSponsor_returns403() throws Exception {
        Customer c = createPersistedCustomer(sponsorA.getId());

        mockMvc.perform(get("/api/customers/{id}", c.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCustomer_admin_canAccessForeignCustomer() throws Exception {
        Customer c = createPersistedCustomer(sponsorA.getId());

        mockMvc.perform(get("/api/customers/{id}", c.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void unsubscribeConfirm_unauthenticated_returnsHtmlWithForm() throws Exception {
        Customer c = createPersistedCustomer(sponsorA.getId());

        mockMvc.perform(get("/api/public/customers/unsubscribe/{token}", c.getUnsubscribeToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString(c.getUnsubscribeToken())));
    }

    @Test
    void unsubscribeConfirm_unknownToken_returnsInvalidHtml() throws Exception {
        mockMvc.perform(get("/api/public/customers/unsubscribe/{token}", "unknown-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void unsubscribePost_validToken_revokesConsentAndIsIdempotent() throws Exception {
        Customer c = createPersistedCustomer(sponsorA.getId());

        mockMvc.perform(post("/api/public/customers/unsubscribe/{token}", c.getUnsubscribeToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

        Optional<Customer> reloaded = customerRepository.findById(c.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().isConsentGranted()).isFalse();

        mockMvc.perform(post("/api/public/customers/unsubscribe/{token}", c.getUnsubscribeToken()))
                .andExpect(status().isOk());
    }

    private Customer createPersistedCustomer(Long sponsorUserId) {
        return customerRepository.save(Customer.builder()
                .name("Test Kunde").email("kunde-" + UUID.randomUUID() + "@example.com")
                .sponsorUserId(sponsorUserId).consentGranted(true)
                .consentGrantedAt(LocalDateTime.now())
                .unsubscribeToken(UUID.randomUUID().toString())
                .build());
    }

    private record CreateReq(String name, String email) {}

    private record CreateFullReq(
            String name, String firstName, String email, String mobilePhone,
            LocalDate birthDate, Integer memberId, String gender, Boolean informalAddress) {}
}
