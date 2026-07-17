package de.toengi.cili.service;

import de.toengi.cili.model.entity.*;
import de.toengi.cili.model.enums.*;
import de.toengi.cili.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AclServiceTest {

    @Mock private AclEntryRepository aclEntryRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserGroupMembershipRepository membershipRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private ResourceRepository resourceRepository;
    @Mock private ObjectProvider<AclService> selfProvider;

    @InjectMocks private AclService aclService;

    private User regularUser;
    private User adminUser;
    private Folder rootFolder;
    private Folder childFolder;

    @BeforeEach
    void setUp() {
        lenient().when(selfProvider.getObject()).thenReturn(aclService);
        regularUser = User.builder().id(10L).username("alice").email("alice@test.com")
                .passwordHash("x").build();
        adminUser = User.builder().id(99L).username("admin").email("admin@test.com")
                .passwordHash("x").role(UserRole.ADMIN).build();
        // rootFolder id=1, path="/1/" → no ancestors
        rootFolder = Folder.builder().id(1L).name("Root").path("/1/").build();
        // childFolder id=2, parent=1, path="/1/2/" → ancestor is folder 1
        childFolder = Folder.builder().id(2L).name("Child").parentId(1L).path("/1/2/").build();
    }

    @Test
    void adminAlwaysGranted() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(adminUser));
        assertThat(aclService.hasPermission(99L, 1L, AclResourceType.FOLDER, AclPermission.READ)).isTrue();
        assertThat(aclService.hasPermission(99L, 1L, AclResourceType.FOLDER, AclPermission.DELETE)).isTrue();
    }

    @Test
    void directUserAllowGrantsPermission() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(regularUser));
        when(membershipRepository.findByUserId(10L)).thenReturn(List.of());
        AclEntry allow = aclEntry(AclSubjectType.USER, 10L, AclResourceType.FOLDER, 1L,
                AclPermission.READ, AclGrantType.ALLOW, true);
        when(aclEntryRepository.findEffectiveEntries(AclResourceType.FOLDER, 1L, 10L, List.of(-1L)))
                .thenReturn(List.of(allow));
        assertThat(aclService.hasPermission(10L, 1L, AclResourceType.FOLDER, AclPermission.READ)).isTrue();
    }

    @Test
    void directUserDenyBlocksPermission() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(regularUser));
        when(membershipRepository.findByUserId(10L)).thenReturn(List.of());
        AclEntry deny = aclEntry(AclSubjectType.USER, 10L, AclResourceType.FOLDER, 1L,
                AclPermission.READ, AclGrantType.DENY, true);
        when(aclEntryRepository.findEffectiveEntries(AclResourceType.FOLDER, 1L, 10L, List.of(-1L)))
                .thenReturn(List.of(deny));
        assertThat(aclService.hasPermission(10L, 1L, AclResourceType.FOLDER, AclPermission.READ)).isFalse();
    }

    @Test
    void userDenyOverridesGroupAllow() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(regularUser));
        UserGroupMembershipId mid = new UserGroupMembershipId(10L, 5L);
        UserGroupMembership membership = UserGroupMembership.builder().id(mid).build();
        when(membershipRepository.findByUserId(10L)).thenReturn(List.of(membership));

        AclEntry deny  = aclEntry(AclSubjectType.USER,  10L, AclResourceType.FOLDER, 1L,
                AclPermission.READ, AclGrantType.DENY, true);
        AclEntry allow = aclEntry(AclSubjectType.GROUP,  5L, AclResourceType.FOLDER, 1L,
                AclPermission.READ, AclGrantType.ALLOW, true);
        when(aclEntryRepository.findEffectiveEntries(AclResourceType.FOLDER, 1L, 10L, List.of(5L)))
                .thenReturn(List.of(deny, allow));

        assertThat(aclService.hasPermission(10L, 1L, AclResourceType.FOLDER, AclPermission.READ)).isFalse();
    }

    @Test
    void groupAllowGrantsWhenNoUserEntry() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(regularUser));
        UserGroupMembershipId mid = new UserGroupMembershipId(10L, 5L);
        UserGroupMembership membership = UserGroupMembership.builder().id(mid).build();
        when(membershipRepository.findByUserId(10L)).thenReturn(List.of(membership));

        AclEntry allow = aclEntry(AclSubjectType.GROUP, 5L, AclResourceType.FOLDER, 1L,
                AclPermission.READ, AclGrantType.ALLOW, true);
        when(aclEntryRepository.findEffectiveEntries(AclResourceType.FOLDER, 1L, 10L, List.of(5L)))
                .thenReturn(List.of(allow));

        assertThat(aclService.hasPermission(10L, 1L, AclResourceType.FOLDER, AclPermission.READ)).isTrue();
    }

    @Test
    void permissionInheritedFromParentFolder() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(regularUser));
        when(membershipRepository.findByUserId(10L)).thenReturn(List.of());
        when(aclEntryRepository.findEffectiveEntries(AclResourceType.FOLDER, 2L, 10L, List.of(-1L)))
                .thenReturn(List.of());
        when(folderRepository.findById(2L)).thenReturn(Optional.of(childFolder));
        AclEntry parentAllow = aclEntry(AclSubjectType.USER, 10L, AclResourceType.FOLDER, 1L,
                AclPermission.READ, AclGrantType.ALLOW, true);
        when(aclEntryRepository.findInheritableEffectiveEntries(AclResourceType.FOLDER, 1L, 10L, List.of(-1L)))
                .thenReturn(List.of(parentAllow));
        assertThat(aclService.hasPermission(10L, 2L, AclResourceType.FOLDER, AclPermission.READ)).isTrue();
    }

    @Test
    void nonInheritableParentEntryDoesNotPropagate() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(regularUser));
        when(membershipRepository.findByUserId(10L)).thenReturn(List.of());
        when(aclEntryRepository.findEffectiveEntries(AclResourceType.FOLDER, 2L, 10L, List.of(-1L)))
                .thenReturn(List.of());
        when(folderRepository.findById(2L)).thenReturn(Optional.of(childFolder));
        when(aclEntryRepository.findInheritableEffectiveEntries(AclResourceType.FOLDER, 1L, 10L, List.of(-1L)))
                .thenReturn(List.of());
        when(aclEntryRepository.findGlobalEffectiveEntries(10L, List.of(-1L))).thenReturn(List.of());
        assertThat(aclService.hasPermission(10L, 2L, AclResourceType.FOLDER, AclPermission.READ)).isFalse();
    }

    @Test
    void globalEntryAppliesWhenNoDirectOrAncestor() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(regularUser));
        when(membershipRepository.findByUserId(10L)).thenReturn(List.of());
        when(aclEntryRepository.findEffectiveEntries(AclResourceType.FOLDER, 1L, 10L, List.of(-1L)))
                .thenReturn(List.of());
        when(folderRepository.findById(1L)).thenReturn(Optional.of(rootFolder));
        // rootFolder path="/1/" → no ancestors → skip ancestor loop
        when(aclEntryRepository.findGlobalEffectiveEntries(10L, List.of(-1L)))
                .thenReturn(List.of(aclEntry(AclSubjectType.USER, 10L, AclResourceType.GLOBAL, null,
                        AclPermission.READ, AclGrantType.ALLOW, true)));
        assertThat(aclService.hasPermission(10L, 1L, AclResourceType.FOLDER, AclPermission.READ)).isTrue();
    }

    @Test
    void defaultDenyWhenNoMatchingEntries() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(regularUser));
        when(membershipRepository.findByUserId(10L)).thenReturn(List.of());
        when(aclEntryRepository.findEffectiveEntries(any(), any(), anyLong(), anyList())).thenReturn(List.of());
        when(folderRepository.findById(1L)).thenReturn(Optional.of(rootFolder));
        when(aclEntryRepository.findGlobalEffectiveEntries(anyLong(), anyList())).thenReturn(List.of());
        assertThat(aclService.hasPermission(10L, 1L, AclResourceType.FOLDER, AclPermission.READ)).isFalse();
    }

    @Test
    void nullFolderResource_grantsRead_toAuthenticatedUser() {
        var resource = de.toengi.cili.model.entity.Resource.builder().id(42L).folderId(null).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(regularUser));
        when(membershipRepository.findByUserId(10L)).thenReturn(List.of());
        when(aclEntryRepository.findEffectiveEntries(AclResourceType.RESOURCE, 42L, 10L, List.of(-1L)))
                .thenReturn(List.of());
        when(resourceRepository.findById(42L)).thenReturn(Optional.of(resource));
        // After the fix, READ on a null-folder resource delegates to hasTestimonialsPermission
        AclEntry testimonialsAllow = aclEntry(AclSubjectType.USER, 10L, AclResourceType.TESTIMONIALS, 0L,
                AclPermission.READ, AclGrantType.ALLOW, false);
        when(aclEntryRepository.findEffectiveEntries(AclResourceType.TESTIMONIALS, 0L, 10L, List.of(-1L)))
                .thenReturn(List.of(testimonialsAllow));

        assertThat(aclService.hasPermission(10L, 42L, AclResourceType.RESOURCE, AclPermission.READ)).isTrue();
    }

    @Test
    void nullFolderResource_deniesWrite_toRegularUser() {
        var resource = de.toengi.cili.model.entity.Resource.builder().id(42L).folderId(null).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(regularUser));
        when(membershipRepository.findByUserId(10L)).thenReturn(List.of());
        when(aclEntryRepository.findEffectiveEntries(AclResourceType.RESOURCE, 42L, 10L, List.of(-1L)))
                .thenReturn(List.of());
        when(resourceRepository.findById(42L)).thenReturn(Optional.of(resource));

        assertThat(aclService.hasPermission(10L, 42L, AclResourceType.RESOURCE, AclPermission.WRITE)).isFalse();
    }

    @Test
    void nullFolderResource_deniesDelete_toRegularUser() {
        var resource = de.toengi.cili.model.entity.Resource.builder().id(42L).folderId(null).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(regularUser));
        when(membershipRepository.findByUserId(10L)).thenReturn(List.of());
        when(aclEntryRepository.findEffectiveEntries(AclResourceType.RESOURCE, 42L, 10L, List.of(-1L)))
                .thenReturn(List.of());
        when(resourceRepository.findById(42L)).thenReturn(Optional.of(resource));

        assertThat(aclService.hasPermission(10L, 42L, AclResourceType.RESOURCE, AclPermission.DELETE)).isFalse();
    }

    @Test
    void parseAncestorIds_rootPath() {
        assertThat(aclService.parseAncestorIds("/")).isEmpty();
    }

    @Test
    void parseAncestorIds_singleSegment() {
        assertThat(aclService.parseAncestorIds("/1/")).isEmpty();
    }

    @Test
    void parseAncestorIds_twoLevels() {
        assertThat(aclService.parseAncestorIds("/1/2/")).containsExactly(1L);
    }

    @Test
    void parseAncestorIds_deepNesting() {
        assertThat(aclService.parseAncestorIds("/1/4/12/")).containsExactly(4L, 1L);
    }

    @Test
    void hasTestimonialsPermission_admin_returnsTrue() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(adminUser));
        assertThat(aclService.hasTestimonialsPermission(99L, AclPermission.READ)).isTrue();
    }

    @Test
    void hasTestimonialsPermission_groupWithAllowEntry_returnsTrue() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(regularUser));
        UserGroupMembership m = new UserGroupMembership();
        UserGroupMembershipId mid = new UserGroupMembershipId();
        mid.setUserId(10L); mid.setGroupId(5L);
        m.setId(mid);
        when(membershipRepository.findByUserId(10L)).thenReturn(List.of(m));
        AclEntry entry = AclEntry.builder()
            .subjectType(AclSubjectType.GROUP).subjectId(5L)
            .resourceType(AclResourceType.TESTIMONIALS).resourceId(0L)
            .permission(AclPermission.READ).grantType(AclGrantType.ALLOW)
            .inheritable(false).build();
        when(aclEntryRepository.findEffectiveEntries(
            AclResourceType.TESTIMONIALS, 0L, 10L, List.of(5L)))
            .thenReturn(List.of(entry));
        assertThat(aclService.hasTestimonialsPermission(10L, AclPermission.READ)).isTrue();
    }

    @Test
    void hasTestimonialsPermission_noEntry_returnsFalse() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(regularUser));
        when(membershipRepository.findByUserId(10L)).thenReturn(List.of());
        when(aclEntryRepository.findEffectiveEntries(
            AclResourceType.TESTIMONIALS, 0L, 10L, List.of(-1L)))
            .thenReturn(List.of());
        assertThat(aclService.hasTestimonialsPermission(10L, AclPermission.READ)).isFalse();
    }

    private AclEntry aclEntry(AclSubjectType subjectType, Long subjectId,
                               AclResourceType resourceType, Long resourceId,
                               AclPermission permission, AclGrantType grantType, boolean inheritable) {
        return AclEntry.builder()
                .subjectType(subjectType).subjectId(subjectId)
                .resourceType(resourceType).resourceId(resourceId)
                .permission(permission).grantType(grantType).inheritable(inheritable)
                .build();
    }
}
