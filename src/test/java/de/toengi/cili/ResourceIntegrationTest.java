package de.toengi.cili;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceIntegrationTest {

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
    @Autowired ResourceMetadataRepository metadataRepository;
    @Autowired ResourceFavoriteRepository favoriteRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserGroupMembershipRepository membershipRepository;
    @Autowired RightsGroupRepository rightsGroupRepository;
    @Autowired StorageService storageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User adminUser;
    private User regularUser;
    private Folder testFolder;
    private Resource testResource;
    private Resource textResource;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        favoriteRepository.deleteAll();
        metadataRepository.deleteAll();
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
                .name("TestFolder").createdBy(adminUser.getId()).build());
        testFolder.setPath("/" + testFolder.getId() + "/");
        testFolder = folderRepository.save(testFolder);

        // Grant alice READ, WRITE, DELETE, DOWNLOAD, MANAGE_METADATA on testFolder
        for (AclPermission perm : new AclPermission[]{
                AclPermission.READ, AclPermission.WRITE, AclPermission.DELETE,
                AclPermission.DOWNLOAD, AclPermission.MANAGE_METADATA}) {
            aclEntryRepository.save(AclEntry.builder()
                    .subjectType(AclSubjectType.USER).subjectId(regularUser.getId())
                    .resourceType(AclResourceType.FOLDER).resourceId(testFolder.getId())
                    .permission(perm).grantType(AclGrantType.ALLOW).inheritable(true)
                    .build());
        }

        // Create a binary resource
        byte[] videoData = "fake video content".getBytes(StandardCharsets.UTF_8);
        String storedName = storageService.store(new ByteArrayInputStream(videoData), videoData.length);
        testResource = resourceRepository.save(Resource.builder()
                .folderId(testFolder.getId()).originalName("video.mp4")
                .storedName(storedName).mimeType("video/mp4")
                .size((long) videoData.length).uploaderId(adminUser.getId())
                .storageType(StorageType.LOCAL).build());

        // Create a text resource
        byte[] textData = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String textStoredName = storageService.store(new ByteArrayInputStream(textData), textData.length);
        textResource = resourceRepository.save(Resource.builder()
                .folderId(testFolder.getId()).originalName("doc.txt")
                .storedName(textStoredName).mimeType("text/plain")
                .size((long) textData.length).uploaderId(adminUser.getId())
                .storageType(StorageType.LOCAL).build());

        adminToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(adminUser));
        userToken  = jwtTokenProvider.generateAccessToken(new CiliUserDetails(regularUser));
    }

    // --- Listing ---

    @Test
    void listByFolder_authenticated_returnsResources() throws Exception {
        mockMvc.perform(get("/api/folders/{folderId}/resources", testFolder.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listByFolder_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/folders/{folderId}/resources", testFolder.getId()))
                .andExpect(status().isUnauthorized());
    }

    // --- Get resource ---

    @Test
    void getResource_returnsDetails() throws Exception {
        mockMvc.perform(get("/api/resources/{id}", testResource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testResource.getId()))
                .andExpect(jsonPath("$.originalName").value("video.mp4"))
                .andExpect(jsonPath("$.mimeType").value("video/mp4"));
    }

    @Test
    void getResource_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/resources/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // --- Download ---

    @Test
    void download_returnsFileContent() throws Exception {
        // StreamingResponseBody is written asynchronously — MockMvc needs asyncDispatch()
        var asyncResult = mockMvc.perform(get("/api/resources/{id}/download", testResource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(request().asyncStarted())
                .andReturn();

        byte[] body = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("video.mp4")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("fake video content");
    }

    // --- Rename ---

    @Test
    void renameResource_updatesName() throws Exception {
        mockMvc.perform(put("/api/resources/{id}", testResource.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalName", "renamed.mp4"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalName").value("renamed.mp4"));

        assertThat(resourceRepository.findById(testResource.getId()).get().getOriginalName())
                .isEqualTo("renamed.mp4");
    }

    // --- Move ---

    @Test
    void moveResource_movesToNewFolder() throws Exception {
        // Create a second folder and grant alice WRITE permission
        Folder targetFolder = folderRepository.save(
                Folder.builder().name("TargetFolder").createdBy(adminUser.getId()).build());
        targetFolder.setPath("/" + targetFolder.getId() + "/");
        targetFolder = folderRepository.save(targetFolder);

        for (AclPermission perm : new AclPermission[]{
                AclPermission.READ, AclPermission.WRITE}) {
            aclEntryRepository.save(AclEntry.builder()
                    .subjectType(AclSubjectType.USER).subjectId(regularUser.getId())
                    .resourceType(AclResourceType.FOLDER).resourceId(targetFolder.getId())
                    .permission(perm).grantType(AclGrantType.ALLOW).inheritable(true)
                    .build());
        }

        mockMvc.perform(patch("/api/resources/{id}/move", testResource.getId())
                        .param("newFolderId", targetFolder.getId().toString())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testResource.getId()))
                .andExpect(jsonPath("$.folderId").value(targetFolder.getId()));

        assertThat(resourceRepository.findById(testResource.getId()).get().getFolderId())
                .isEqualTo(targetFolder.getId());
    }

    @Test
    void moveResource_noWritePermissionOnTarget_returns403() throws Exception {
        // Create a folder alice has no WRITE access to
        Folder restrictedFolder = folderRepository.save(
                Folder.builder().name("RestrictedFolder").createdBy(adminUser.getId()).build());
        restrictedFolder.setPath("/" + restrictedFolder.getId() + "/");
        folderRepository.save(restrictedFolder);

        mockMvc.perform(patch("/api/resources/{id}/move", testResource.getId())
                        .param("newFolderId", restrictedFolder.getId().toString())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // --- Delete ---

    @Test
    void deleteResource_removesFromDb() throws Exception {
        mockMvc.perform(delete("/api/resources/{id}", testResource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        assertThat(resourceRepository.findById(testResource.getId())).isEmpty();
    }

    // --- Metadata ---

    @Test
    void updateMetadata_savesMetadata() throws Exception {
        mockMvc.perform(put("/api/resources/{id}/metadata", testResource.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "My Video", "description", "A test video",
                                "tags", "test,video", "language", "en"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("My Video"));

        assertThat(metadataRepository.findByResourceId(testResource.getId())).isPresent();
    }

    // --- Favorites ---

    @Test
    void addAndRemoveFavorite() throws Exception {
        // Add
        mockMvc.perform(post("/api/resources/{id}/favorite", testResource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        assertThat(favoriteRepository.existsById(
                new ResourceFavoriteId(regularUser.getId(), testResource.getId()))).isTrue();

        // List favorites
        mockMvc.perform(get("/api/resources/favorites")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Remove
        mockMvc.perform(delete("/api/resources/{id}/favorite", testResource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        assertThat(favoriteRepository.existsById(
                new ResourceFavoriteId(regularUser.getId(), testResource.getId()))).isFalse();
    }

    // --- Text editor ---

    @Test
    void getContent_returnsTextContent() throws Exception {
        String body = mockMvc.perform(get("/api/resources/{id}/content", textResource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEqualTo("Hello, World!");
    }

    @Test
    void saveContent_updatesFileAndTextContent() throws Exception {
        mockMvc.perform(put("/api/resources/{id}/content", textResource.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Updated content".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isNoContent());

        String updatedContent = mockMvc.perform(
                        get("/api/resources/{id}/content", textResource.getId())
                                .header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getContentAsString();
        assertThat(updatedContent).isEqualTo("Updated content");
    }

    @Test
    void noReadPermission_getResource_returns403() throws Exception {
        // Create a folder alice has no access to
        Folder other = folderRepository.save(
                Folder.builder().name("Other").createdBy(adminUser.getId()).build());
        other.setPath("/" + other.getId() + "/");
        folderRepository.save(other);

        byte[] data = "secret".getBytes(StandardCharsets.UTF_8);
        String sn = storageService.store(new ByteArrayInputStream(data), data.length);
        Resource secret = resourceRepository.save(Resource.builder()
                .folderId(other.getId()).originalName("secret.txt")
                .storedName(sn).mimeType("text/plain").size(6L)
                .uploaderId(adminUser.getId()).storageType(StorageType.LOCAL).build());

        mockMvc.perform(get("/api/resources/{id}", secret.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}
