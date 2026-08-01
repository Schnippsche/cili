package de.toengi.cili;

import de.toengi.cili.model.entity.Testimonial;
import de.toengi.cili.model.entity.User;
import de.toengi.cili.model.enums.UserRole;
import de.toengi.cili.repository.TestimonialRepository;
import de.toengi.cili.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the public, unauthenticated testimonial share endpoint
 * (GET /api/public/testimonials/{id}), exercised through the real Spring Security
 * filter chain to confirm /api/public/** is actually permitAll() and that an
 * unknown id is mapped to 404 by the global exception handler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicTestimonialIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired TestimonialRepository testimonialRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        testimonialRepository.deleteAll();
        userRepository.deleteAll();

        owner = userRepository.save(User.builder()
                .username("owner").email("owner@test.com")
                .passwordHash("$2a$12$x").role(UserRole.ADMIN).build());
    }

    @Test
    void getOne_anonymousRequest_returnsOkWithTestimonialData() throws Exception {
        Testimonial testimonial = testimonialRepository.save(Testimonial.builder()
                .authorName("Anna Mustermann").text("Sehr zufrieden mit dem Produkt.")
                .isHuman(true).isAnimal(false)
                .userId(owner.getId())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/public/testimonials/{id}", testimonial.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorName").value("Anna Mustermann"));
    }

    @Test
    void getOne_unknownId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/public/testimonials/{id}", 999999L))
                .andExpect(status().isNotFound());
    }
}
