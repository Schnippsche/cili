package de.toengi.cili;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.dto.auth.LoginRequest;
import de.toengi.cili.dto.auth.LoginResponse;
import de.toengi.cili.model.entity.User;
import de.toengi.cili.model.enums.UserRole;
import de.toengi.cili.repository.RefreshTokenRepository;
import de.toengi.cili.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminLogIntegrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("cili.logs.path", tempDir::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        Files.deleteIfExists(tempDir.resolve("cili.log"));

        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .username("admin")
                .email("admin@example.com")
                .passwordHash(passwordEncoder.encode("adminpass"))
                .role(UserRole.ADMIN)
                .build());

        String response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "adminpass"))))
                .andReturn().getResponse().getContentAsString();
        adminToken = objectMapper.readValue(response, LoginResponse.class).accessToken();
    }

    @Test
    void getLogs_withExistingFile_returns200WithLines() throws Exception {
        Files.writeString(tempDir.resolve("cili.log"),
                "2026-06-19 10:00:00.000 [main] INFO  de.toengi.cili.App - Started\n" +
                "2026-06-19 10:00:01.000 [main] WARN  de.toengi.cili.App - Warning\n");

        mvc.perform(get("/api/admin/logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines").isArray())
                .andExpect(jsonPath("$.lines[0]").value(
                        "2026-06-19 10:00:00.000 [main] INFO  de.toengi.cili.App - Started"))
                .andExpect(jsonPath("$.totalLines").value(2))
                .andExpect(jsonPath("$.lastModified").isString());
    }

    @Test
    void getLogs_withMissingFile_returns404() throws Exception {
        mvc.perform(get("/api/admin/logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getLogs_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/admin/logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getLogs_linesParamLimitsCap() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            sb.append("2026-06-19 10:00:0").append(i).append(".000 [main] INFO app - line ").append(i).append("\n");
        }
        Files.writeString(tempDir.resolve("cili.log"), sb.toString());

        mvc.perform(get("/api/admin/logs?lines=3")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLines").value(3))
                .andExpect(jsonPath("$.lines[2]").value(
                        "2026-06-19 10:00:010.000 [main] INFO app - line 10"));
    }
}
