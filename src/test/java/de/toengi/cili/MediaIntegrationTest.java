package de.toengi.cili;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.toengi.cili.dto.media.VideoResumeDto;
import de.toengi.cili.model.entity.*;
import de.toengi.cili.model.enums.*;
import de.toengi.cili.repository.*;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.security.JwtTokenProvider;
import de.toengi.cili.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaIntegrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideStorageBasePath(DynamicPropertyRegistry registry) {
        registry.add("cili.storage.base-path", () -> tempDir.toAbsolutePath().toString());
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired AclEntryRepository aclEntryRepository;
    @Autowired ResourceRepository resourceRepository;
    @Autowired SubtitleTrackRepository subtitleTrackRepository;
    @Autowired ThumbnailRepository thumbnailRepository;
    @Autowired VideoResumeRepository videoResumeRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserGroupMembershipRepository membershipRepository;
    @Autowired RightsGroupRepository rightsGroupRepository;
    @Autowired StorageService storageService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private User adminUser;
    private User regularUser;
    private Folder testFolder;
    private Resource videoResource;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        videoResumeRepository.deleteAll();
        subtitleTrackRepository.deleteAll();
        thumbnailRepository.deleteAll();
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
                .name("MediaFolder").createdBy(adminUser.getId()).build());
        testFolder.setPath("/" + testFolder.getId() + "/");
        testFolder = folderRepository.save(testFolder);

        // Grant alice READ, DOWNLOAD, MANAGE_SUBTITLES on testFolder
        for (AclPermission perm : new AclPermission[]{
                AclPermission.READ, AclPermission.DOWNLOAD, AclPermission.MANAGE_SUBTITLES}) {
            aclEntryRepository.save(AclEntry.builder()
                    .subjectType(AclSubjectType.USER).subjectId(regularUser.getId())
                    .resourceType(AclResourceType.FOLDER).resourceId(testFolder.getId())
                    .permission(perm).grantType(AclGrantType.ALLOW).inheritable(true)
                    .build());
        }

        // Create a video resource with real file on disk
        byte[] videoData = "fake video bytes for streaming test".getBytes(StandardCharsets.UTF_8);
        String storedName = storageService.store(new ByteArrayInputStream(videoData), videoData.length);
        videoResource = resourceRepository.save(Resource.builder()
                .folderId(testFolder.getId()).originalName("movie.mp4")
                .storedName(storedName).mimeType("video/mp4")
                .size((long) videoData.length).uploaderId(adminUser.getId())
                .storageType(StorageType.LOCAL).build());

        userToken  = jwtTokenProvider.generateAccessToken(new CiliUserDetails(regularUser));
    }

    // --- Streaming ---

    @Test
    void stream_noRangeHeader_returns200WithContent() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/stream", videoResource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"));
    }

    @Test
    void stream_withRangeHeader_returns206PartialContent() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/stream", videoResource.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .header("Range", "bytes=0-9"))
                .andExpect(status().isPartialContent());
    }

    @Test
    void stream_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/stream", videoResource.getId()))
                .andExpect(status().isUnauthorized());
    }

    // --- Subtitles ---

    @Test
    void uploadSubtitle_listAndDownloadAndDelete() throws Exception {
        byte[] vttContent = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHello\n".getBytes(StandardCharsets.UTF_8);

        // Upload
        String trackJson = mockMvc.perform(multipart("/api/resources/{id}/subtitles", videoResource.getId())
                        .file(new MockMultipartFile("file", "subtitle.vtt", "text/vtt", vttContent))
                        .param("languageCode", "en")
                        .param("label", "English")
                        .param("format", "VTT")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.languageCode").value("en"))
                .andExpect(jsonPath("$.format").value("VTT"))
                .andReturn().getResponse().getContentAsString();

        Long trackId = objectMapper.readTree(trackJson).get("id").asLong();

        // List
        mockMvc.perform(get("/api/resources/{id}/subtitles", videoResource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].languageCode").value("en"));

        // Download
        byte[] downloaded = mockMvc.perform(
                        get("/api/resources/{id}/subtitles/{trackId}", videoResource.getId(), trackId)
                                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(vttContent);

        // Delete
        mockMvc.perform(delete("/api/resources/{id}/subtitles/{trackId}", videoResource.getId(), trackId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        assertThat(subtitleTrackRepository.findByResourceId(videoResource.getId())).isEmpty();
    }

    @Test
    void uploadSubtitle_duplicateLanguage_returns409() throws Exception {
        byte[] vttContent = "WEBVTT\n\n".getBytes();

        mockMvc.perform(multipart("/api/resources/{id}/subtitles", videoResource.getId())
                        .file(new MockMultipartFile("file", "en.vtt", "text/vtt", vttContent))
                        .param("languageCode", "en").param("format", "VTT")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/resources/{id}/subtitles", videoResource.getId())
                        .file(new MockMultipartFile("file", "en2.vtt", "text/vtt", vttContent))
                        .param("languageCode", "en").param("format", "VTT")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isConflict());
    }

    // --- Video Resume ---

    @Test
    void saveAndGetResume_roundTrips() throws Exception {
        // Save position
        mockMvc.perform(put("/api/resources/{id}/resume", videoResource.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VideoResumeDto(videoResource.getId(), 120, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionSeconds").value(120));

        // Get it back
        mockMvc.perform(get("/api/resources/{id}/resume", videoResource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionSeconds").value(120));
    }

    @Test
    void getResume_noEntry_returnsZero() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/resume", videoResource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionSeconds").value(0));
    }

    // --- Thumbnail endpoint (DONE status required) ---

    @Test
    void getThumbnail_notDone_returns404() throws Exception {
        // No thumbnail entry → 404
        mockMvc.perform(get("/api/resources/{id}/thumbnail", videoResource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }
}
