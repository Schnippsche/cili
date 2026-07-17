package de.toengi.cili;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.model.entity.*;
import de.toengi.cili.model.enums.*;
import de.toengi.cili.repository.*;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.security.JwtTokenProvider;
import de.toengi.cili.service.storage.StorageService;
import org.junit.jupiter.api.BeforeAll;
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

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Echter Ende-zu-Ende-Integrationstest für die Video-Clip-Erstellung: realer Spring-Context,
 * reale DB (H2), reales FFmpeg-Binary — analog zu {@link MediaIntegrationTest} und
 * {@link UploadIntegrationTest}.
 *
 * <p>Das Test-Profil (application-test.yml) übernimmt für {@code cili.storage.ffmpeg-path} /
 * {@code cili.storage.ffprobe-path} unverändert die Produktions-Defaults ({@code /usr/bin/ffmpeg}
 * bzw. {@code /usr/bin/ffprobe}), die auf einer Windows-Dev-Maschine nicht existieren. Ebenso ist
 * {@code cili.ffmpeg.video-codec} standardmäßig auf {@code h264_nvenc} (GPU-Encoder) gesetzt, der
 * nicht auf jeder Maschine/CI verfügbar ist. Für einen plattformübergreifend lauffähigen Test
 * werden diese Properties unten per {@link DynamicPropertySource} auf plattformneutrale, über
 * PATH auflösbare Kommandos ({@code ffmpeg}/{@code ffprobe}) bzw. den überall verfügbaren
 * Software-Encoder {@code libx264} umgestellt — das ist keine Mock-Ersetzung, sondern exakt der
 * gleiche Mechanismus, mit dem {@code MediaIntegrationTest} bereits {@code cili.storage.base-path}
 * auf ein Test-Verzeichnis umbiegt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VideoClipIntegrationTest {

    @TempDir
    static Path tempDir;

    private static byte[] testVideoBytes;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("cili.storage.base-path", () -> tempDir.toAbsolutePath().toString());
        // Plattformneutrale Kommandonamen statt der fest verdrahteten /usr/bin/-Pfade aus
        // application.yml — die JVM löst diese via PATH auf (funktioniert unter Windows/Linux/macOS).
        registry.add("cili.storage.ffmpeg-path", () -> "ffmpeg");
        registry.add("cili.storage.ffprobe-path", () -> "ffprobe");
        // Software-Encoder statt des Default-h264_nvenc, damit der Test auch ohne
        // NVIDIA-GPU/-Treiber (z.B. auf CI) deterministisch läuft.
        registry.add("cili.ffmpeg.video-codec", () -> "libx264");
        registry.add("cili.ffmpeg.temp-dir",
                () -> tempDir.resolve("ffmpeg-tmp").toAbsolutePath().toString());
    }

    @BeforeAll
    static void generateTestVideo() throws Exception {
        Path videoFile = Files.createTempFile("cili-clip-source", ".mp4");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-loglevel", "error",
                    "-f", "lavfi", "-i", "testsrc=duration=3:size=160x120:rate=10",
                    "-f", "lavfi", "-i", "sine=frequency=1000:duration=3",
                    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest",
                    videoFile.toAbsolutePath().toString());
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "Konnte Test-Fixture-Video nicht erzeugen: ffmpeg-Timeout nach 30s. "
                                + "Ist 'ffmpeg' auf dem PATH installiert?");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "Konnte Test-Fixture-Video nicht erzeugen: ffmpeg exit=" + process.exitValue());
            }
            testVideoBytes = Files.readAllBytes(videoFile);
        } finally {
            Files.deleteIfExists(videoFile);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired AclEntryRepository aclEntryRepository;
    @Autowired ResourceRepository resourceRepository;
    @Autowired ProcessingJobRepository processingJobRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserGroupMembershipRepository membershipRepository;
    @Autowired RightsGroupRepository rightsGroupRepository;
    @Autowired StorageService storageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User adminUser;
    private User regularUser;
    private Folder testFolder;
    private Resource videoResource;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        processingJobRepository.deleteAll();
        resourceRepository.deleteAll();
        aclEntryRepository.deleteAll();
        folderRepository.deleteAll();
        membershipRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        rightsGroupRepository.deleteAll();

        adminUser = userRepository.save(User.builder()
                .username("admin").email("admin@test.com")
                .passwordHash("$2a$12$x").role(UserRole.ADMIN).build());

        regularUser = userRepository.save(User.builder()
                .username("alice").email("alice@test.com")
                .passwordHash("$2a$12$x").build());

        testFolder = folderRepository.save(Folder.builder()
                .name("ClipFolder").createdBy(adminUser.getId()).build());
        testFolder.setPath("/" + testFolder.getId() + "/");
        testFolder = folderRepository.save(testFolder);

        // alice braucht READ (fuer den @PreAuthorize-Check auf der Resource) und UPLOAD
        // (fuer VideoClipService.enqueueClip's expliziten Zielordner-Check) auf testFolder.
        for (AclPermission perm : new AclPermission[]{AclPermission.READ, AclPermission.UPLOAD}) {
            aclEntryRepository.save(AclEntry.builder()
                    .subjectType(AclSubjectType.USER).subjectId(regularUser.getId())
                    .resourceType(AclResourceType.FOLDER).resourceId(testFolder.getId())
                    .permission(perm).grantType(AclGrantType.ALLOW).inheritable(true)
                    .build());
        }

        // Reale Video-Datei auf Platte (per echtem FFmpeg erzeugt) — direkt über Repository/
        // StorageService angelegt statt über den chunked Upload-Endpoint, damit kein
        // ResourceUploadedEvent für die Quell-Resource feuert (analog MediaIntegrationTest).
        String storedName = storageService.store(new ByteArrayInputStream(testVideoBytes), testVideoBytes.length);
        videoResource = resourceRepository.save(Resource.builder()
                .folderId(testFolder.getId()).originalName("source.mp4")
                .storedName(storedName).mimeType("video/mp4")
                .size((long) testVideoBytes.length).uploaderId(adminUser.getId())
                .storageType(StorageType.LOCAL).build());

        userToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(regularUser));
    }

    @Test
    void createClip_fromUploadedVideo_producesNewPlayableResource() throws Exception {
        String requestJson = """
                {"startMs": 0, "endMs": 2000}""";

        String createResponse = mockMvc.perform(post("/api/resources/{id}/clip", videoResource.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").isNumber())
                .andReturn().getResponse().getContentAsString();

        long jobId = objectMapper.readTree(createResponse).get("jobId").asLong();
        assertThat(jobId).isPositive();

        // Die Clip-Erstellung läuft asynchron auf dem echten transcodeExecutor-Thread-Pool
        // (TestAsyncConfig) — daher pollen statt synchron zu prüfen. Timeout 30s.
        JsonNode activeJobs = pollUntilNoActiveClipJobs(jobId);
        assertThat(activeJobs.isEmpty())
                .as("VIDEO_CLIP-Job sollte innerhalb von 30s fertig sein (PENDING/RUNNING -> DONE/FAILED)")
                .isTrue();

        String resourcesJson = mockMvc.perform(get("/api/folders/{folderId}/resources", testFolder.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode resources = objectMapper.readTree(resourcesJson);
        assertThat(resources.isArray()).isTrue();

        boolean hasClipResource = false;
        for (JsonNode node : resources) {
            String originalName = node.path("originalName").asText();
            String mimeType = node.path("mimeType").asText();
            if (originalName.endsWith("_00-00-00_00-00-02.mp4") && "video/mp4".equals(mimeType)) {
                hasClipResource = true;
                assertThat(node.path("size").asLong())
                        .as("clip resource should have non-zero size (real FFmpeg output)")
                        .isGreaterThan(0);
            }
        }

        assertThat(hasClipResource)
                .as("expected an additional video/mp4 resource named '<original>_00-00-00_00-00-02.mp4' "
                        + "in folder listing, got: " + resourcesJson)
                .isTrue();
        assertThat(resources).hasSizeGreaterThan(1);
    }

    private JsonNode pollUntilNoActiveClipJobs(long jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        JsonNode activeJobs;
        while (true) {
            String activeJson = mockMvc.perform(get("/api/resources/{id}/clip-jobs/active", videoResource.getId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            activeJobs = objectMapper.readTree(activeJson);
            if (activeJobs.isEmpty()) {
                break;
            }

            // ProcessingJobService.markFailed() setzt einen fehlgeschlagenen Job bei
            // attempts < maxAttempts zurück auf PENDING statt auf FAILED — und nichts holt
            // VIDEO_CLIP-Jobs erneut ab (kein Scheduler/Claim-Loop für diesen Job-Typ). Ein
            // echter FFmpeg-Fehlschlag sähe also über /clip-jobs/active für den Rest des
            // Timeouts identisch aus wie ein noch laufender Job. errorMessage direkt an der
            // Job-Zeile prüfen, um sofort mit der echten Ursache zu failen statt 30s auf ein
            // Timeout zu warten, das den eigentlichen Fehler verschleiert.
            ProcessingJob job = processingJobRepository.findById(jobId).orElse(null);
            if (job != null && job.getErrorMessage() != null) {
                fail("Clip job failed: " + job.getErrorMessage());
            }

            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            Thread.sleep(500);
        }
        return activeJobs;
    }
}
