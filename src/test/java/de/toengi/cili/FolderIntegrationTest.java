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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FolderIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired RightsGroupRepository rightsGroupRepository;
    @Autowired UserGroupMembershipRepository membershipRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired FolderFavoriteRepository favoriteRepository;
    @Autowired AclEntryRepository aclEntryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User adminUser;
    private User regularUser;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        aclEntryRepository.deleteAll();
        favoriteRepository.deleteAll();
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

        adminToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(adminUser));
        userToken  = jwtTokenProvider.generateAccessToken(new CiliUserDetails(regularUser));
    }

    private Folder root(String name) {
        Folder f = folderRepository.save(
                Folder.builder().name(name).createdBy(adminUser.getId()).build());
        f.setPath("/" + f.getId() + "/");
        return folderRepository.save(f);
    }

    private Folder child(String name, Folder parent) {
        Folder f = folderRepository.save(
                Folder.builder().name(name).parentId(parent.getId())
                        .createdBy(adminUser.getId()).build());
        f.setPath(parent.getPath() + f.getId() + "/");
        return folderRepository.save(f);
    }

    private void grant(AclPermission perm, User user, Folder folder) {
        aclEntryRepository.save(AclEntry.builder()
                .subjectType(AclSubjectType.USER).subjectId(user.getId())
                .resourceType(AclResourceType.FOLDER).resourceId(folder.getId())
                .permission(perm).grantType(AclGrantType.ALLOW).inheritable(true).build());
    }

    // --- create ---

    @Test
    void admin_createRootFolder_returns201WithCorrectPath() throws Exception {
        String resp = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Projects"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Projects"))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(resp).get("id").asLong();
        assertThat(objectMapper.readTree(resp).get("path").asText()).isEqualTo("/" + id + "/");
    }

    @Test
    void regularUser_createRootFolder_returns403() throws Exception {
        mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "X"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userWithWrite_createSubfolder_returns201() throws Exception {
        Folder r = root("Root");
        grant(AclPermission.WRITE, regularUser, r);

        String resp = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Sub", "parentId", r.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(r.getId()))
                .andReturn().getResponse().getContentAsString();
        Long newId = objectMapper.readTree(resp).get("id").asLong();
        assertThat(objectMapper.readTree(resp).get("path").asText())
                .isEqualTo(r.getPath() + newId + "/");
    }

    // --- get ---

    @Test
    void getFolder_withRead_returns200() throws Exception {
        Folder r = root("Root");
        grant(AclPermission.READ, regularUser, r);
        mockMvc.perform(get("/api/folders/{id}", r.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Root"));
    }

    @Test
    void getFolder_withoutPermission_returns403() throws Exception {
        Folder r = root("Root");
        mockMvc.perform(get("/api/folders/{id}", r.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // --- listChildren ---

    @Test
    void listChildren_returnsDirectChildrenOnly() throws Exception {
        Folder r = root("Root");
        Folder c = child("Child", r);
        child("Grandchild", c);
        grant(AclPermission.READ, regularUser, r);

        mockMvc.perform(get("/api/folders/{id}/children", r.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Child"));
    }

    // --- listRootFolders ---

    @Test
    void listRootFolders_regularUser_onlyPermittedFolders() throws Exception {
        Folder visible = root("Visible");
        root("Hidden");
        grant(AclPermission.READ, regularUser, visible);

        mockMvc.perform(get("/api/folders/root")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Visible"));
    }

    @Test
    void listRootFolders_admin_returnsAll() throws Exception {
        root("A");
        root("B");
        mockMvc.perform(get("/api/folders/root")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // --- update ---

    @Test
    void updateFolder_withWrite_renames() throws Exception {
        Folder r = root("Old");
        grant(AclPermission.WRITE, regularUser, r);

        mockMvc.perform(patch("/api/folders/{id}", r.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "New"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"));
    }

    // --- move ---

    @Test
    void admin_moveFolder_updatesDescendantPaths() throws Exception {
        Folder src = root("Src");
        Folder c = child("Child", src);
        Folder dest = root("Dest");

        mockMvc.perform(put("/api/folders/{id}/move", src.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .param("newParentId", dest.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(dest.getId()));

        String expectedChildPrefix = dest.getPath() + src.getId() + "/";
        assertThat(folderRepository.findById(c.getId()).orElseThrow().getPath())
                .startsWith(expectedChildPrefix);
    }

    // --- trash + restore + purge ---

    @Test
    void trashFolder_softDeletesWithDescendants() throws Exception {
        Folder r = root("Root");
        Folder c = child("Child", r);
        grant(AclPermission.DELETE, regularUser, r);

        mockMvc.perform(delete("/api/folders/{id}", r.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        assertThat(folderRepository.findById(r.getId()).orElseThrow().isTrashed()).isTrue();
        assertThat(folderRepository.findById(c.getId()).orElseThrow().isTrashed()).isTrue();
    }

    @Test
    void admin_restoreThenPurge() throws Exception {
        Folder r = root("ToDelete");
        r.setTrashed(true);
        folderRepository.save(r);

        mockMvc.perform(post("/api/folders/{id}/restore", r.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trashed").value(false));

        r.setTrashed(true);
        folderRepository.save(r);

        mockMvc.perform(delete("/api/folders/{id}/purge", r.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(folderRepository.findById(r.getId())).isEmpty();
    }

    // --- breadcrumb ---

    @Test
    void getBreadcrumb_returnsPathFromRootToSelf() throws Exception {
        Folder r = root("Root");
        Folder c = child("Child", r);
        Folder gc = child("Grandchild", c);

        mockMvc.perform(get("/api/folders/{id}/breadcrumb", gc.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Root"))
                .andExpect(jsonPath("$[1].name").value("Child"))
                .andExpect(jsonPath("$[2].name").value("Grandchild"));
    }

    // --- favorites ---

    @Test
    void addRemoveFavorite_roundtrip() throws Exception {
        Folder r = root("Fav");

        mockMvc.perform(post("/api/folders/{id}/favorite", r.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/folders/favorites")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(r.getId()));

        mockMvc.perform(delete("/api/folders/{id}/favorite", r.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/folders/favorites")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    // --- unauthenticated ---

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/folders/root"))
                .andExpect(status().isUnauthorized());
    }
}
