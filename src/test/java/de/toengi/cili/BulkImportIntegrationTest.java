package de.toengi.cili;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BulkImportIntegrationTest {

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
    @Autowired BulkImportItemRepository bulkImportItemRepository;
    @Autowired BulkImportJobRepository bulkImportJobRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User adminUser;
    private User aliceUser;
    private Folder targetFolder;
    private String adminToken;
    private String aliceToken;

    @BeforeEach
    void setUp() {
        uploadJobRepository.deleteAll();
        bulkImportItemRepository.deleteAll();
        bulkImportJobRepository.deleteAll();
        resourceRepository.deleteAll();
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

        targetFolder = folderRepository.save(
                Folder.builder().name("Kampagnen").createdBy(adminUser.getId()).build());
        targetFolder.setPath("/" + targetFolder.getId() + "/");
        targetFolder = folderRepository.save(targetFolder);

        adminToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(adminUser));
        aliceToken  = jwtTokenProvider.generateAccessToken(new CiliUserDetails(aliceUser));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private String createBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "targetFolderId", targetFolder.getId(),
                "rootName", "Mai2026",
                "entries", List.of(
                        Map.of("relativePath", "Interviews/video1.mp4",
                                "fileSize", 1000, "mimeType", "video/mp4"),
                        Map.of("relativePath", "notes.exe",
                                "fileSize", 500, "mimeType", "application/x-msdownload")
                )
        ));
    }

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test
    void nonAdmin_create_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/bulk-imports")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_create_resolvesFolderTreeAndClassifiesEntries() throws Exception {
        String response = mockMvc.perform(post("/api/admin/bulk-imports")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        String jobId = objectMapper.readTree(response).get("jobId").asText();

        assertThat(folderRepository.findByParentIdAndName(targetFolder.getId(), "Interviews")).hasSize(1);

        mockMvc.perform(get("/api/admin/bulk-imports/{jobId}", jobId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.filesTotal").value(2))
                .andExpect(jsonPath("$.filesSkipped").value(1));
    }

    @Test
    void otherAdmin_getJob_returns403() throws Exception {
        String response = mockMvc.perform(post("/api/admin/bulk-imports")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(response).get("jobId").asText();

        User otherAdmin = userRepository.save(User.builder()
                .username("admin2").email("admin2@test.com")
                .passwordHash("$2a$12$x").role(UserRole.ADMIN).build());
        String otherAdminToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(otherAdmin));

        mockMvc.perform(get("/api/admin/bulk-imports/{jobId}", jobId)
                        .header("Authorization", "Bearer " + otherAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherAdmin_failItem_returns403() throws Exception {
        // Spiegelt otherAdmin_getJob_returns403 — ohne den Ownership-Guard in
        // BulkImportService.failItem könnte jeder Admin fremde Items als FAILED markieren.
        String response = mockMvc.perform(post("/api/admin/bulk-imports")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(response).get("jobId").asText();
        long pendingItemId = objectMapper.readTree(response).get("items").get(0).get("id").asLong();

        User otherAdmin = userRepository.save(User.builder()
                .username("admin3").email("admin3@test.com")
                .passwordHash("$2a$12$x").role(UserRole.ADMIN).build());
        String otherAdminToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(otherAdmin));

        mockMvc.perform(post("/api/admin/bulk-imports/{jobId}/items/{itemId}/fail", jobId, pendingItemId)
                        .header("Authorization", "Bearer " + otherAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"errorMessage\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void failItem_ownJobIdButItemFromAnotherAdminsJob_returns404AndLeavesOtherJobUntouched() throws Exception {
        // Regression test: admin A owns jobA (created below via adminToken); admin B owns jobB
        // with itemB. Admin A calls fail on jobA's id + itemB's id — the jobId ownership check
        // passes (jobA really does belong to admin A), but itemB belongs to a completely
        // different job. Without BulkImportService.failItem's item-belongs-to-job guard, this
        // would let any admin mark an arbitrary other admin's item as FAILED and corrupt the
        // WRONG job's filesFailed counter.
        String jobAResponse = mockMvc.perform(post("/api/admin/bulk-imports")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String jobAId = objectMapper.readTree(jobAResponse).get("jobId").asText();

        User adminB = userRepository.save(User.builder()
                .username("adminB").email("adminB@test.com")
                .passwordHash("$2a$12$x").role(UserRole.ADMIN).build());
        String adminBToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(adminB));

        String jobBResponse = mockMvc.perform(post("/api/admin/bulk-imports")
                        .header("Authorization", "Bearer " + adminBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String jobBId = objectMapper.readTree(jobBResponse).get("jobId").asText();
        long itemBId = objectMapper.readTree(jobBResponse).get("items").get(0).get("id").asLong();

        // Admin A: own jobId (jobA) + admin B's itemId (itemB) — must be rejected.
        mockMvc.perform(post("/api/admin/bulk-imports/{jobId}/items/{itemId}/fail", jobAId, itemBId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"errorMessage\":\"x\"}"))
                .andExpect(status().isNotFound());

        // itemB / jobB must be completely unaffected by the rejected attempt.
        BulkImportItem itemB = bulkImportItemRepository.findById(itemBId).orElseThrow();
        assertThat(itemB.getStatus()).isEqualTo(BulkImportItemStatus.PENDING);
        assertThat(itemB.getErrorMessage()).isNull();
        assertThat(bulkImportJobRepository.findById(jobBId).orElseThrow().getFilesFailed()).isEqualTo(0);

        mockMvc.perform(get("/api/admin/bulk-imports/{jobId}", jobBId)
                        .header("Authorization", "Bearer " + adminBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.filesFailed").value(0));
    }

    @Test
    void failItem_marksFailedAndReflectsInJobStatus() throws Exception {
        String response = mockMvc.perform(post("/api/admin/bulk-imports")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(response).get("jobId").asText();
        long pendingItemId = objectMapper.readTree(response).get("items").get(0).get("id").asLong();

        mockMvc.perform(post("/api/admin/bulk-imports/{jobId}/items/{itemId}/fail", jobId, pendingItemId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"errorMessage\":\"Verbindung verloren\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/bulk-imports/{jobId}", jobId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.filesFailed").value(1));
    }

    @Test
    void initUpload_blockedMimeTypeOnBulkItem_marksItemFailedIndependentlyOfRollback() throws Exception {
        // Regression test for the Chunk 3 transaction-boundary fix in UploadService.initUpload:
        // UploadServiceTest.initUpload_bulkImportItemBlockedMimeType_marksItemFailedInsteadOfStuck()
        // (a Mockito unit test) cannot prove the REQUIRES_NEW commit boundary actually holds,
        // because its mocked PlatformTransactionManager has no real rollback semantics. This test
        // uses a genuine JpaTransactionManager + H2 to prove it end-to-end: initUpload() itself
        // throws (its own @Transactional rolls back), yet the BulkImportItem still ends up FAILED
        // and the job's filesFailed counter still increments — which is only possible if
        // markBulkItemFailed()'s explicit REQUIRES_NEW transaction genuinely committed on its own.
        //
        // "diagram.svg" / image/svg+xml is chosen because BulkImportService classifies all
        // "image/*" prefixes as supported (so createImport puts the item in PENDING, not SKIPPED),
        // while UploadService.BLOCKED_MIME_TYPES separately denylists image/svg+xml — exactly the
        // supported-by-bulk-import-but-blocked-by-upload gap the fix addresses.
        String createBody = objectMapper.writeValueAsString(Map.of(
                "targetFolderId", targetFolder.getId(),
                "rootName", "Mai2026",
                "entries", List.of(
                        Map.of("relativePath", "diagram.svg",
                                "fileSize", 100, "mimeType", "image/svg+xml")
                )
        ));

        String createResponse = mockMvc.perform(post("/api/admin/bulk-imports")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        String jobId = objectMapper.readTree(createResponse).get("jobId").asText();
        long itemId = objectMapper.readTree(createResponse).get("items").get(0).get("id").asLong();
        long resolvedFolderId = objectMapper.readTree(createResponse).get("items").get(0)
                .get("resolvedFolderId").asLong();

        String initBody = objectMapper.writeValueAsString(Map.of(
                "fileName", "diagram.svg",
                "mimeType", "image/svg+xml",
                "totalSize", 100,
                "chunkSize", 1000,
                "folderId", resolvedFolderId,
                "bulkImportItemId", itemId
        ));

        // The init-upload request itself fails — UploadService.initUpload throws CiliException
        // (400) after detecting the blocked MIME type, and its own @Transactional rolls back.
        mockMvc.perform(post("/api/uploads/init")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody))
                .andExpect(status().isBadRequest());

        // Yet the BulkImportItem is FAILED and the job's filesFailed counter is incremented —
        // this is only observable if markBulkItemFailed()'s REQUIRES_NEW transaction committed
        // independently of (and despite) the rollback of the enclosing initUpload transaction.
        mockMvc.perform(get("/api/admin/bulk-imports/{jobId}", jobId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.filesFailed").value(1))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].errorMessage").value("Dateityp nicht erlaubt: image/svg+xml"));

        BulkImportItem persisted = bulkImportItemRepository.findById(itemId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(BulkImportItemStatus.FAILED);
        assertThat(bulkImportJobRepository.findById(jobId).orElseThrow().getFilesFailed()).isEqualTo(1);

        // No UploadJob or Resource was ever created — initUpload rolled back before persisting them.
        assertThat(uploadJobRepository.findAll()).isEmpty();
        assertThat(resourceRepository.findAll()).isEmpty();
    }

    @Test
    void initUpload_blockedMimeTypeOnBulkItem_retryDoesNotDoubleCountFailure() throws Exception {
        // Regression test for the whole-branch review finding: the frontend's upload hook
        // (useBulkImportUpload.ts / uploadOne) retries POST /api/uploads/init on ANY thrown
        // error, including this deterministic 400 for a blocked MIME type — so the real client
        // calls this endpoint multiple times for the same bulkImportItemId. Without the
        // terminal-state guard added to UploadService.markBulkItemFailed, each retry would
        // re-increment filesFailed for what is really a single failure, corrupting the job's
        // derived progress. This end-to-end test proves the guard holds against a real DB/
        // transaction manager, not just the mocked UploadServiceTest unit test.
        String createBody = objectMapper.writeValueAsString(Map.of(
                "targetFolderId", targetFolder.getId(),
                "rootName", "Mai2026",
                "entries", List.of(
                        Map.of("relativePath", "diagram.svg",
                                "fileSize", 100, "mimeType", "image/svg+xml")
                )
        ));

        String createResponse = mockMvc.perform(post("/api/admin/bulk-imports")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String jobId = objectMapper.readTree(createResponse).get("jobId").asText();
        long itemId = objectMapper.readTree(createResponse).get("items").get(0).get("id").asLong();
        long resolvedFolderId = objectMapper.readTree(createResponse).get("items").get(0)
                .get("resolvedFolderId").asLong();

        String initBody = objectMapper.writeValueAsString(Map.of(
                "fileName", "diagram.svg",
                "mimeType", "image/svg+xml",
                "totalSize", 100,
                "chunkSize", 1000,
                "folderId", resolvedFolderId,
                "bulkImportItemId", itemId
        ));

        // Two retries of the exact same request — both must fail with the same 400.
        mockMvc.perform(post("/api/uploads/init")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/uploads/init")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody))
                .andExpect(status().isBadRequest());

        // filesFailed must still be 1, not 2 — the second call's markBulkItemFailed must have
        // no-op'd because the item was already FAILED from the first call.
        mockMvc.perform(get("/api/admin/bulk-imports/{jobId}", jobId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.filesFailed").value(1))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"));

        assertThat(bulkImportJobRepository.findById(jobId).orElseThrow().getFilesFailed()).isEqualTo(1);
    }
}
