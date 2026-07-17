package de.toengi.cili;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.model.entity.*;
import de.toengi.cili.model.enums.*;
import de.toengi.cili.repository.*;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadIntegrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideStorageBasePath(DynamicPropertyRegistry registry) {
        registry.add("cili.storage.base-path", () -> tempDir.toAbsolutePath().toString());
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @Autowired UserRepository userRepository;
    @Autowired RightsGroupRepository rightsGroupRepository;
    @Autowired UserGroupMembershipRepository membershipRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired AclEntryRepository aclEntryRepository;
    @Autowired ResourceRepository resourceRepository;
    @Autowired UploadJobRepository uploadJobRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User adminUser;
    private User aliceUser;
    private Folder testFolder;
    private String adminToken;
    private String aliceToken;

    @BeforeEach
    void setUp() {
        resourceRepository.deleteAll();
        uploadJobRepository.deleteAll();
        aclEntryRepository.deleteAll();
        folderRepository.deleteAll();
        membershipRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        rightsGroupRepository.deleteAll();

        adminUser = userRepository.save(User.builder()
                .username("admin").email("admin@test.com")
                .passwordHash("$2a$12$x")
                .role(UserRole.ADMIN).build());

        aliceUser = userRepository.save(User.builder()
                .username("alice").email("alice@test.com")
                .passwordHash("$2a$12$x").build());

        testFolder = folderRepository.save(
                Folder.builder().name("TestFolder").createdBy(adminUser.getId()).build());
        testFolder.setPath("/" + testFolder.getId() + "/");
        testFolder = folderRepository.save(testFolder);

        // Grant UPLOAD permission to alice on testFolder
        aclEntryRepository.save(AclEntry.builder()
                .subjectType(AclSubjectType.USER).subjectId(aliceUser.getId())
                .resourceType(AclResourceType.FOLDER).resourceId(testFolder.getId())
                .permission(AclPermission.UPLOAD).grantType(AclGrantType.ALLOW).inheritable(true)
                .build());

        adminToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(adminUser));
        aliceToken  = jwtTokenProvider.generateAccessToken(new CiliUserDetails(aliceUser));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private String initBody(int chunkSize, int numChunks) throws Exception {
        long totalSize = (long) chunkSize * numChunks;
        return objectMapper.writeValueAsString(Map.of(
                "fileName", "test.bin",
                "mimeType", "application/octet-stream",
                "totalSize", totalSize,
                "chunkSize", chunkSize,
                "folderId", testFolder.getId()
        ));
    }

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test
    void unauthenticated_initUpload_returns401() throws Exception {
        mockMvc.perform(post("/api/uploads/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody(1024, 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noUploadPermission_initUpload_returns403() throws Exception {
        // alice has UPLOAD, but create a new user with no permissions
        User bob = userRepository.save(User.builder()
                .username("bob").email("bob@test.com")
                .passwordHash("$2a$12$x").build());
        String bobToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(bob));

        mockMvc.perform(post("/api/uploads/init")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody(1024, 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullFlow_singleChunk_createsResource() throws Exception {
        // Init
        String initResponse = mockMvc.perform(post("/api/uploads/init")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody(1024, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chunksTotal").value(1))
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andReturn().getResponse().getContentAsString();

        String jobId = objectMapper.readTree(initResponse).get("jobId").asText();

        // Upload chunk 0
        byte[] chunkData = new byte[1024];
        mockMvc.perform(put("/api/uploads/{jobId}/chunk/0", jobId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(chunkData))
                .andExpect(status().isOk());

        // Complete
        String completeResponse = mockMvc.perform(post("/api/uploads/{jobId}/complete", jobId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").isNumber())
                .andReturn().getResponse().getContentAsString();

        Long resourceId = objectMapper.readTree(completeResponse).get("resourceId").asLong();
        assertThat(resourceRepository.findById(resourceId)).isPresent();
    }

    @Test
    void fullFlow_multipleChunks_assemblesCorrectly() throws Exception {
        int chunkSize = 512;

        // Init 2 chunks
        String initResponse = mockMvc.perform(post("/api/uploads/init")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody(chunkSize, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chunksTotal").value(2))
                .andReturn().getResponse().getContentAsString();

        String jobId = objectMapper.readTree(initResponse).get("jobId").asText();

        byte[] chunk = new byte[chunkSize];

        // Upload chunk 0
        mockMvc.perform(put("/api/uploads/{jobId}/chunk/0", jobId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(chunk))
                .andExpect(status().isOk());

        // Upload chunk 1
        mockMvc.perform(put("/api/uploads/{jobId}/chunk/1", jobId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(chunk))
                .andExpect(status().isOk());

        // Complete
        String completeResponse = mockMvc.perform(post("/api/uploads/{jobId}/complete", jobId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").isNumber())
                .andReturn().getResponse().getContentAsString();

        Long resourceId = objectMapper.readTree(completeResponse).get("resourceId").asLong();
        assertThat(resourceRepository.findById(resourceId)).isPresent();
    }

    @Test
    void getStatus_nonExistentJob_returns404() throws Exception {
        mockMvc.perform(get("/api/uploads/nonexistent-job-id/status")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void completeUpload_missingChunk_returns409() throws Exception {
        // Init 2 chunks
        String initResponse = mockMvc.perform(post("/api/uploads/init")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody(512, 2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String jobId = objectMapper.readTree(initResponse).get("jobId").asText();

        // Upload only chunk 0 — skip chunk 1
        mockMvc.perform(put("/api/uploads/{jobId}/chunk/0", jobId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[512]))
                .andExpect(status().isOk());

        // Complete with missing chunk 1 → 409 Conflict
        mockMvc.perform(post("/api/uploads/{jobId}/complete", jobId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelUpload_removesJob() throws Exception {
        String initResponse = mockMvc.perform(post("/api/uploads/init")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody(1024, 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String jobId = objectMapper.readTree(initResponse).get("jobId").asText();

        mockMvc.perform(delete("/api/uploads/{jobId}", jobId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        assertThat(uploadJobRepository.findById(jobId)).isEmpty();
    }

    @Test
    void uploadChunk_wrongOwner_returns403() throws Exception {
        // Admin initiates (ADMIN role bypasses ACL check for init)
        String initResponse = mockMvc.perform(post("/api/uploads/init")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody(1024, 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String jobId = objectMapper.readTree(initResponse).get("jobId").asText();

        // Alice tries to upload chunk for admin's job → 403
        mockMvc.perform(put("/api/uploads/{jobId}/chunk/0", jobId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[1024]))
                .andExpect(status().isForbidden());
    }
}
