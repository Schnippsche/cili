package de.toengi.cili;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.toengi.cili.dto.translation.SubtitleTranslationRequest;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SubtitleTranslationIntegrationTest {

    @TempDir static Path tempDir;

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry r) {
        r.add("cili.storage.base-path", () -> tempDir.toAbsolutePath().toString());
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired LanguageOptionRepository languageOptionRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired AclEntryRepository aclEntryRepository;
    @Autowired ResourceRepository resourceRepository;
    @Autowired SubtitleTrackRepository subtitleTrackRepository;
    @Autowired ProcessingJobRepository processingJobRepository;
    @Autowired UserGroupMembershipRepository membershipRepository;
    @Autowired RightsGroupRepository rightsGroupRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    User adminUser;
    User regularUser;
    Folder testFolder;
    Resource videoResource;
    SubtitleTrack deTrack;
    String adminToken;
    String userToken;
    String noPermToken;

    @BeforeEach
    void setUp() {
        // Deletion order respects FK constraints (children before parents)
        processingJobRepository.deleteAll();
        subtitleTrackRepository.deleteAll();
        aclEntryRepository.deleteAll();   // before folder + user
        resourceRepository.deleteAll();
        folderRepository.deleteAll();
        membershipRepository.deleteAll(); // before user + group
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        rightsGroupRepository.deleteAll();

        languageOptionRepository.saveAll(List.of(
            LanguageOption.builder().code("de").label("Deutsch").translationSupported(true).sortOrder(10).enabled(true).build(),
            LanguageOption.builder().code("en").label("English").translationSupported(true).sortOrder(20).enabled(true).build(),
            LanguageOption.builder().code("pl").label("Polski").translationSupported(true).sortOrder(90).enabled(true).build()
        ));

        adminUser = userRepository.save(User.builder()
                .username("admin").email("admin@test.com")
                .passwordHash("$2a$12$x").role(UserRole.ADMIN).build());

        regularUser = userRepository.save(User.builder()
                .username("alice").email("alice@test.com")
                .passwordHash("$2a$12$x").build());

        User noPermUser = userRepository.save(User.builder()
                .username("bob").email("bob@test.com")
                .passwordHash("$2a$12$x").build());

        testFolder = folderRepository.save(Folder.builder()
                .name("TestFolder").createdBy(adminUser.getId()).build());
        testFolder.setPath("/" + testFolder.getId() + "/");
        testFolder = folderRepository.save(testFolder);

        // Alice gets READ + MANAGE_SUBTITLES + TRANSLATE_SUBTITLES on the folder
        for (AclPermission p : new AclPermission[]{
                AclPermission.READ, AclPermission.MANAGE_SUBTITLES, AclPermission.TRANSLATE_SUBTITLES}) {
            aclEntryRepository.save(AclEntry.builder()
                    .subjectType(AclSubjectType.USER).subjectId(regularUser.getId())
                    .resourceType(AclResourceType.FOLDER).resourceId(testFolder.getId())
                    .permission(p).grantType(AclGrantType.ALLOW).inheritable(true)
                    .build());
        }

        // Bob gets only READ (no MANAGE_SUBTITLES / TRANSLATE_SUBTITLES)
        aclEntryRepository.save(AclEntry.builder()
                .subjectType(AclSubjectType.USER).subjectId(noPermUser.getId())
                .resourceType(AclResourceType.FOLDER).resourceId(testFolder.getId())
                .permission(AclPermission.READ).grantType(AclGrantType.ALLOW).inheritable(true)
                .build());

        videoResource = resourceRepository.save(Resource.builder()
                .folderId(testFolder.getId()).originalName("film.mp4")
                .storedName("fake-uuid").mimeType("video/mp4")
                .size(1000L).uploaderId(adminUser.getId())
                .storageType(StorageType.LOCAL).build());

        deTrack = subtitleTrackRepository.save(SubtitleTrack.builder()
                .resourceId(videoResource.getId())
                .languageCode("de").label("Deutsch")
                .storedName("de-uuid").format(SubtitleFormat.VTT)
                .textContent("WEBVTT\n\n1\n00:00:01.000 --> 00:00:03.000\nHallo Welt\n")
                .build());

        adminToken  = jwtTokenProvider.generateAccessToken(new CiliUserDetails(adminUser));
        userToken   = jwtTokenProvider.generateAccessToken(new CiliUserDetails(regularUser));
        noPermToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(noPermUser));
    }

    @Test
    void requestTranslation_202AndJobIdReturned() throws Exception {
        mockMvc.perform(post("/api/resources/{id}/subtitle-translations", videoResource.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                new SubtitleTranslationRequest(deTrack.getId(), "pl"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", notNullValue()));
    }

    @Test
    void requestTranslation_conflictReturns409() throws Exception {
        // Create existing PL track
        subtitleTrackRepository.save(SubtitleTrack.builder()
                .resourceId(videoResource.getId())
                .languageCode("pl").label("Polski")
                .storedName("pl-uuid").format(SubtitleFormat.VTT)
                .textContent("WEBVTT\n").build());

        mockMvc.perform(post("/api/resources/{id}/subtitle-translations", videoResource.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                new SubtitleTranslationRequest(deTrack.getId(), "pl"))))
                .andExpect(status().isConflict());
    }

    @Test
    void requestTranslation_overwriteDeletesExistingAndReturns202() throws Exception {
        subtitleTrackRepository.save(SubtitleTrack.builder()
                .resourceId(videoResource.getId())
                .languageCode("pl").label("Polski")
                .storedName("pl-uuid").format(SubtitleFormat.VTT)
                .textContent("WEBVTT\n").build());

        mockMvc.perform(post("/api/resources/{id}/subtitle-translations", videoResource.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("overwrite", "true")
                        .content(om.writeValueAsString(
                                new SubtitleTranslationRequest(deTrack.getId(), "pl"))))
                .andExpect(status().isAccepted());

        // Old track was deleted; only 1 PL track remains (from the new job — but it's async,
        // so the track might not be created yet. Just verify no 409 and old track gone.)
        assertThat(subtitleTrackRepository
                .findByResourceIdAndLanguageCode(videoResource.getId(), "pl"))
                .isEmpty();  // deleted before async job completes
    }

    @Test
    void requestTranslation_unknownLangReturns400() throws Exception {
        mockMvc.perform(post("/api/resources/{id}/subtitle-translations", videoResource.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                new SubtitleTranslationRequest(deTrack.getId(), "xx"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestTranslation_unknownSourceTrackReturns404() throws Exception {
        mockMvc.perform(post("/api/resources/{id}/subtitle-translations", videoResource.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                new SubtitleTranslationRequest(99999L, "pl"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestTranslation_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/resources/{id}/subtitle-translations", videoResource.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                new SubtitleTranslationRequest(deTrack.getId(), "pl"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestTranslation_withoutManageSubtitlesPermReturns403() throws Exception {
        mockMvc.perform(post("/api/resources/{id}/subtitle-translations", videoResource.getId())
                        .header("Authorization", "Bearer " + noPermToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                new SubtitleTranslationRequest(deTrack.getId(), "pl"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getActiveJobs_returnsEmptyListWhenNoJobs() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/subtitle-translations/active",
                        videoResource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getActiveJobs_afterRequestReturnsJob() throws Exception {
        // Create a job first
        mockMvc.perform(post("/api/resources/{id}/subtitle-translations", videoResource.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                new SubtitleTranslationRequest(deTrack.getId(), "en"))))
                .andExpect(status().isAccepted());

        // GET active jobs
        mockMvc.perform(get("/api/resources/{id}/subtitle-translations/active",
                        videoResource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("SUBTITLE_TRANSLATE"));
    }
}
