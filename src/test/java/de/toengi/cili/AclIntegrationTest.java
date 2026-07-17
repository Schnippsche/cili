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
class AclIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @Autowired UserRepository userRepository;
    @Autowired RightsGroupRepository rightsGroupRepository;
    @Autowired UserGroupMembershipRepository membershipRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired AclEntryRepository aclEntryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User adminUser;
    private User regularUser;
    private Folder rootFolder;
    private Folder childFolder;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
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

        regularUser = userRepository.save(User.builder()
                .username("alice").email("alice@test.com")
                .passwordHash("$2a$12$x").build());

        rootFolder = folderRepository.save(
                Folder.builder().name("Root").createdBy(adminUser.getId()).build());
        rootFolder.setPath("/" + rootFolder.getId() + "/");
        rootFolder = folderRepository.save(rootFolder);

        childFolder = folderRepository.save(
                Folder.builder().name("Child").parentId(rootFolder.getId())
                        .createdBy(adminUser.getId())
                        .path("/" + rootFolder.getId() + "/").build());
        childFolder.setPath("/" + rootFolder.getId() + "/" + childFolder.getId() + "/");
        childFolder = folderRepository.save(childFolder);

        adminToken = jwtTokenProvider.generateAccessToken(new CiliUserDetails(adminUser));
        userToken  = jwtTokenProvider.generateAccessToken(new CiliUserDetails(regularUser));
    }

    @Test
    void unauthenticated_listEntries_returns401() throws Exception {
        mockMvc.perform(get("/api/acl/folders/{id}/entries", rootFolder.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUser_createEntry_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "subjectType", "USER",
                "subjectId", regularUser.getId(),
                "permission", "READ",
                "grantType", "ALLOW",
                "inheritable", true
        ));
        mockMvc.perform(post("/api/acl/folders/{id}/entries", rootFolder.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_createEntry_thenList_thenDelete() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "subjectType", "USER",
                "subjectId", regularUser.getId(),
                "permission", "READ",
                "grantType", "ALLOW",
                "inheritable", true
        ));

        // Create
        String responseJson = mockMvc.perform(post("/api/acl/folders/{id}/entries", rootFolder.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permission").value("READ"))
                .andExpect(jsonPath("$.grantType").value("ALLOW"))
                .andExpect(jsonPath("$.inheritable").value(true))
                .andReturn().getResponse().getContentAsString();

        Long entryId = objectMapper.readTree(responseJson).get("id").asLong();

        // List
        mockMvc.perform(get("/api/acl/folders/{id}/entries", rootFolder.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(entryId));

        // Delete
        mockMvc.perform(delete("/api/acl/entries/{id}", entryId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(aclEntryRepository.findById(entryId)).isEmpty();
    }

    @Test
    void delete_nonExistentEntry_returns404() throws Exception {
        mockMvc.perform(delete("/api/acl/entries/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void userWithDirectReadAllow_effectivePermissionsContainsRead() throws Exception {
        aclEntryRepository.save(AclEntry.builder()
                .subjectType(AclSubjectType.USER).subjectId(regularUser.getId())
                .resourceType(AclResourceType.FOLDER).resourceId(rootFolder.getId())
                .permission(AclPermission.READ).grantType(AclGrantType.ALLOW).inheritable(true)
                .build());

        mockMvc.perform(get("/api/acl/folders/{id}/effective-permissions", rootFolder.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectUserId").value(regularUser.getId()))
                .andExpect(jsonPath("$.permissions", hasItem("READ")));
    }

    @Test
    void userWithNoEntries_effectivePermissionsEmpty() throws Exception {
        mockMvc.perform(get("/api/acl/folders/{id}/effective-permissions", rootFolder.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", empty()));
    }

    @Test
    void admin_queryOtherUserEffectivePermissions() throws Exception {
        aclEntryRepository.save(AclEntry.builder()
                .subjectType(AclSubjectType.USER).subjectId(regularUser.getId())
                .resourceType(AclResourceType.FOLDER).resourceId(rootFolder.getId())
                .permission(AclPermission.WRITE).grantType(AclGrantType.ALLOW).inheritable(true)
                .build());

        mockMvc.perform(get("/api/acl/folders/{folderId}/effective-permissions/{userId}",
                        rootFolder.getId(), regularUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectUserId").value(regularUser.getId()))
                .andExpect(jsonPath("$.permissions", hasItem("WRITE")));
    }

    @Test
    void adminUser_effectivePermissionsContainsAll() throws Exception {
        mockMvc.perform(get("/api/acl/folders/{id}/effective-permissions", rootFolder.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions",
                        hasItems("READ", "WRITE", "DELETE", "DOWNLOAD", "UPLOAD",
                                "SHARE", "MANAGE_METADATA", "MANAGE_SUBTITLES", "TRANSLATE_SUBTITLES", "ADMIN")));
    }

    @Test
    void readOnParent_inheritedToChild() throws Exception {
        aclEntryRepository.save(AclEntry.builder()
                .subjectType(AclSubjectType.USER).subjectId(regularUser.getId())
                .resourceType(AclResourceType.FOLDER).resourceId(rootFolder.getId())
                .permission(AclPermission.READ).grantType(AclGrantType.ALLOW).inheritable(true)
                .build());

        mockMvc.perform(get("/api/acl/folders/{id}/effective-permissions", childFolder.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("READ")));
    }

    @Test
    void nonInheritableOnParent_notPropagatedToChild() throws Exception {
        aclEntryRepository.save(AclEntry.builder()
                .subjectType(AclSubjectType.USER).subjectId(regularUser.getId())
                .resourceType(AclResourceType.FOLDER).resourceId(rootFolder.getId())
                .permission(AclPermission.READ).grantType(AclGrantType.ALLOW).inheritable(false)
                .build());

        mockMvc.perform(get("/api/acl/folders/{id}/effective-permissions", childFolder.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", not(hasItem("READ"))));
    }
}
