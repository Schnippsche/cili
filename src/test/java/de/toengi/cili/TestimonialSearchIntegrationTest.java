package de.toengi.cili;

import de.toengi.cili.model.entity.*;
import de.toengi.cili.model.enums.*;
import de.toengi.cili.repository.*;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Erzwingt einen frischen, synchron aufgebauten ApplicationContext für diese Klasse: läuft sie
// direkt nach SubtitleTranslationIntegrationTest (das @DirtiesContext(AFTER_CLASS) setzt), kann
// sonst ein Race zwischen Context-Neuaufbau (create-drop-Schema) und dieser Klasse @BeforeEach
// auftreten — die H2-Testdatenbank ist dann kurzzeitig leer ("Table not found").
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class TestimonialSearchIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired TestimonialRepository testimonialRepository;
    @Autowired ResourceRepository resourceRepository;
    @Autowired SubtitleTrackRepository subtitleTrackRepository;

    private User adminUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        subtitleTrackRepository.deleteAll(); // child of resources
        resourceRepository.deleteAll();      // child of testimonials + users
        testimonialRepository.deleteAll();   // child of users
        userRepository.deleteAll();          // root

        adminUser = userRepository.save(User.builder()
                .username("admin").email("admin@test.com")
                .passwordHash("$2a$12$x").role(UserRole.ADMIN).build());

        adminToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(adminUser));
    }

    private Testimonial saveTestimonial(String authorName, String text) {
        return testimonialRepository.save(Testimonial.builder()
                .authorName(authorName).text(text).source("Mensch")
                .userId(adminUser.getId())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());
    }

    private Resource saveVideoAttachment(Long testimonialId) {
        return resourceRepository.save(Resource.builder()
                .testimonialId(testimonialId)
                .originalName("clip.mp4")
                .storedName(UUID.randomUUID().toString())
                .mimeType("video/mp4")
                .size(1024L)
                .uploaderId(adminUser.getId())
                .build());
    }

    @Test
    void search_matchesBySubtitleContent_returnsTestimonial() throws Exception {
        Testimonial testimonial = saveTestimonial("Anna Mustermann", "Sehr zufrieden mit dem Produkt.");
        Resource video = saveVideoAttachment(testimonial.getId());
        subtitleTrackRepository.save(SubtitleTrack.builder()
                .resourceId(video.getId())
                .languageCode("de")
                .label("Deutsch")
                .storedName(UUID.randomUUID().toString())
                .format(SubtitleFormat.SRT)
                .textContent("Das Video zeigt eine besondere Überraschung im Alltag")
                .build());

        mockMvc.perform(get("/api/testimonials")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("q", "Überraschung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].authorName").value("Anna Mustermann"));
    }

    @Test
    void search_noSubtitleMatch_returnsEmpty() throws Exception {
        Testimonial testimonial = saveTestimonial("Bruno Beispiel", "Alles bestens gelaufen.");
        Resource video = saveVideoAttachment(testimonial.getId());
        subtitleTrackRepository.save(SubtitleTrack.builder()
                .resourceId(video.getId())
                .languageCode("de")
                .label("Deutsch")
                .storedName(UUID.randomUUID().toString())
                .format(SubtitleFormat.SRT)
                .textContent("Ein ganz normaler Tag ohne besondere Vorkommnisse")
                .build());

        mockMvc.perform(get("/api/testimonials")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("q", "Zauberwort-das-nirgends-vorkommt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void search_subtitleOfOtherTestimonial_doesNotMatch() throws Exception {
        Testimonial matching = saveTestimonial("Clara Correct", "Standardtext");
        Testimonial other = saveTestimonial("Dirk Daneben", "Anderer Standardtext");
        Resource videoMatching = saveVideoAttachment(matching.getId());
        Resource videoOther = saveVideoAttachment(other.getId());
        subtitleTrackRepository.save(SubtitleTrack.builder()
                .resourceId(videoMatching.getId()).languageCode("de").label("Deutsch")
                .storedName(UUID.randomUUID().toString()).format(SubtitleFormat.SRT)
                .textContent("Einzigartiges Suchwort Bratapfel").build());
        subtitleTrackRepository.save(SubtitleTrack.builder()
                .resourceId(videoOther.getId()).languageCode("de").label("Deutsch")
                .storedName(UUID.randomUUID().toString()).format(SubtitleFormat.SRT)
                .textContent("Vollkommen anderer Inhalt ohne Treffer").build());

        mockMvc.perform(get("/api/testimonials")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("q", "Bratapfel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].authorName").value("Clara Correct"));
    }
}
