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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;


import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired RightsGroupRepository rightsGroupRepository;
    @Autowired UserGroupMembershipRepository membershipRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired AclEntryRepository aclEntryRepository;
    @Autowired ResourceRepository resourceRepository;
    @Autowired ResourceMetadataRepository metadataRepository;
    @Autowired SubtitleTrackRepository subtitleTrackRepository;
    @Autowired UploadJobRepository uploadJobRepository;

    private User adminUser;
    private Folder testFolder;
    private String adminToken;

    @BeforeEach
    void setUp() {
        subtitleTrackRepository.deleteAll(); // child of resources
        metadataRepository.deleteAll();      // child of resources
        resourceRepository.deleteAll();     // child of folders + users
        uploadJobRepository.deleteAll();    // child of folders + users
        aclEntryRepository.deleteAll();     // standalone
        membershipRepository.deleteAll();   // child of users + groups
        refreshTokenRepository.deleteAll(); // child of users
        folderRepository.deleteAll();       // child of users (created_by)
        userRepository.deleteAll();         // root
        rightsGroupRepository.deleteAll();  // root

        adminUser = userRepository.save(User.builder()
                .username("admin").email("admin@test.com")
                .passwordHash("$2a$12$x").role(UserRole.ADMIN).build());

        testFolder = folderRepository.save(
                Folder.builder().name("TestFolder").createdBy(adminUser.getId()).build());
        testFolder.setPath("/" + testFolder.getId() + "/");
        testFolder = folderRepository.save(testFolder);

        adminToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(adminUser));
    }

    private Resource saveResource(String name, String mimeType) {
        return resourceRepository.save(Resource.builder()
                .folderId(testFolder.getId())
                .originalName(name)
                .storedName(UUID.randomUUID().toString())
                .mimeType(mimeType)
                .size(1024L)
                .uploaderId(adminUser.getId())
                .build());
    }

    @Test
    void unauthenticated_search_returns401() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void search_noResults_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("q", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(0))
                .andExpect(jsonPath("$.hits", hasSize(0)));
    }

    @Test
    void search_matchesByName_returnsResult() throws Exception {
        saveResource("important-report.pdf", "application/pdf");
        saveResource("vacation-photo.jpg", "image/jpeg");

        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("q", "report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.hits[0].name", containsString("report")));
    }

    @Test
    void search_emptyQuery_returnsAllResources() throws Exception {
        saveResource("file-a.txt", "text/plain");
        saveResource("file-b.txt", "text/plain");

        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(2));
    }

    @Test
    void search_withFolderFilter_returnsOnlyThatFolder() throws Exception {
        saveResource("in-folder.pdf", "application/pdf");

        Folder otherFolder = folderRepository.save(
                Folder.builder().name("Other").createdBy(adminUser.getId()).build());
        otherFolder.setPath("/" + otherFolder.getId() + "/");
        folderRepository.save(otherFolder);
        resourceRepository.save(Resource.builder()
                .folderId(otherFolder.getId())
                .originalName("other-folder.pdf")
                .storedName(UUID.randomUUID().toString())
                .mimeType("application/pdf").size(512L)
                .uploaderId(adminUser.getId()).build());

        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("q", "pdf")
                        .param("folder", String.valueOf(testFolder.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.hits[0].name").value("in-folder.pdf"));
    }

    @Test
    void search_pagination_respectsPageAndSize() throws Exception {
        for (int i = 1; i <= 5; i++) {
            saveResource("document-" + i + ".txt", "text/plain");
        }

        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("q", "document")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(5))
                .andExpect(jsonPath("$.hits", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    void getFacets_returnsMimeTypeCounts() throws Exception {
        saveResource("video.mp4", "video/mp4");
        saveResource("video2.mp4", "video/mp4");
        saveResource("document.pdf", "application/pdf");

        mockMvc.perform(get("/api/search/facets")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mimeTypes", hasSize(2)))
                .andExpect(jsonPath("$.mimeTypes[?(@.value == 'video/mp4')].count",
                        contains(2)))
                .andExpect(jsonPath("$.mimeTypes[?(@.value == 'application/pdf')].count",
                        contains(1)));
    }

    @Test
    void search_mimeTypeFilter_degradesGracefully_inFallback() throws Exception {
        saveResource("video.mp4", "video/mp4");
        saveResource("image.png", "image/png");

        // With ES down, mimeType filter is ignored but results still return (MySQL fallback)
        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("q", "")
                        .param("mimeType", "video/mp4"))
                .andExpect(status().isOk())
                // mimeType filter not supported in MySQL search
                .andExpect(jsonPath("$.totalHits").value(2));
    }

    @Test
    void search_matchesBySubtitleContent_returnsVideo() throws Exception {
        Resource video = saveResource("interview.mp4", "video/mp4");
        metadataRepository.save(ResourceMetadata.builder()
                .resourceId(video.getId()).title("Interview").build());
        subtitleTrackRepository.save(SubtitleTrack.builder()
                .resourceId(video.getId())
                .languageCode("de")
                .label("Deutsch")
                .storedName(UUID.randomUUID().toString())
                .format(SubtitleFormat.SRT)
                .textContent("Hallo Welt, das ist ein Test")
                .build());

        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("q", "Hallo Welt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.hits[0].name").value("interview.mp4"));
    }

    @Test
    void search_multipleSubtitleTracks_returnsVideoOnce() throws Exception {
        Resource video = saveResource("lecture.mp4", "video/mp4");
        subtitleTrackRepository.save(SubtitleTrack.builder()
                .resourceId(video.getId()).languageCode("de").label("Deutsch")
                .storedName(UUID.randomUUID().toString()).format(SubtitleFormat.SRT)
                .textContent("Willkommen zur Vorlesung").build());
        subtitleTrackRepository.save(SubtitleTrack.builder()
                .resourceId(video.getId()).languageCode("en").label("English")
                .storedName(UUID.randomUUID().toString()).format(SubtitleFormat.SRT)
                .textContent("Welcome to the lecture").build());

        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("q", "lecture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1));
    }
}
