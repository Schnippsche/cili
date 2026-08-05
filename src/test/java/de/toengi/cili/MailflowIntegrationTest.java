package de.toengi.cili;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.model.entity.Customer;
import de.toengi.cili.model.entity.User;
import de.toengi.cili.model.enums.UserRole;
import de.toengi.cili.repository.CustomerRepository;
import de.toengi.cili.repository.MailflowInstanceRepository;
import de.toengi.cili.repository.MailflowStepStatusRepository;
import de.toengi.cili.repository.UserRepository;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.security.JwtTokenProvider;
import de.toengi.cili.service.MailflowStepProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MailflowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired MailflowInstanceRepository instanceRepository;
    @Autowired MailflowStepStatusRepository stepRepository;
    @Autowired MailflowStepProcessor stepProcessor;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private User sponsorA;
    private String tokenA;
    private Customer customer;

    @BeforeEach
    void setUp() {
        stepRepository.deleteAll();
        instanceRepository.deleteAll();
        customerRepository.deleteAll();
        userRepository.deleteAll();

        sponsorA = userRepository.save(User.builder()
                .username("sponsorA").email("sponsora@test.com").passwordHash("x").build());
        tokenA = jwtTokenProvider.generateAccessToken(new CiliUserDetails(sponsorA));

        customer = customerRepository.save(Customer.builder()
                .name("Anna Beispiel").email("anna-" + UUID.randomUUID() + "@example.com")
                .sponsorUserId(sponsorA.getId()).consentGranted(true)
                .consentGrantedAt(LocalDateTime.now())
                .unsubscribeToken(UUID.randomUUID().toString())
                .build());
    }

    @Test
    void listAvailable_returnsEmptyByDefault() throws Exception {
        mockMvc.perform(get("/api/mailflows").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void startFlow_unknownFlowName_returns404() throws Exception {
        String body = objectMapper.writeValueAsString(new StartReq("does-not-exist"));

        mockMvc.perform(post("/api/customers/{id}/mailflows", customer.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void startFlow_foreignSponsor_returns403() throws Exception {
        User sponsorB = userRepository.save(User.builder()
                .username("sponsorB").email("sponsorb@test.com").passwordHash("x").build());
        String tokenB = jwtTokenProvider.generateAccessToken(new CiliUserDetails(sponsorB));
        String body = objectMapper.writeValueAsString(new StartReq("onboarding"));

        mockMvc.perform(post("/api/customers/{id}/mailflows", customer.getId())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void listInstances_noInstancesYet_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/customers/{id}/mailflows", customer.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void processDueSteps_customerOptedOut_marksAllDueStepsSkipped() {
        customer.setConsentGranted(false);
        customer.setConsentRevokedAt(LocalDateTime.now());
        customerRepository.save(customer);

        var instance = instanceRepository.save(de.toengi.cili.model.entity.MailflowInstance.builder()
                .customerId(customer.getId()).flowName("onboarding")
                .startedAt(LocalDateTime.now()).createdByUserId(sponsorA.getId()).build());
        var step = stepRepository.save(de.toengi.cili.model.entity.MailflowStepStatus.builder()
                .instanceId(instance.getId()).stepId("welcome")
                .scheduledFor(java.time.LocalDate.now())
                .status(de.toengi.cili.model.enums.MailflowStepState.PENDING).build());

        stepProcessor.processDueSteps();

        var reloaded = stepRepository.findById(step.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(de.toengi.cili.model.enums.MailflowStepState.SKIPPED);
    }

    private record StartReq(String flowName) {}
}
