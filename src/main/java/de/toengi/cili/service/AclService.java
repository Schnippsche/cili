package de.toengi.cili.service;

import de.toengi.cili.dto.acl.CreateAclEntryRequest;
import de.toengi.cili.dto.acl.CreateCollectionsAclEntryRequest;
import de.toengi.cili.dto.acl.CreateTestimonialsAclEntryRequest;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.AclEntry;
import de.toengi.cili.model.entity.Folder;
import de.toengi.cili.model.entity.User;
import de.toengi.cili.model.enums.AclGrantType;
import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.AclResourceType;
import de.toengi.cili.model.enums.AclSubjectType;
import de.toengi.cili.model.enums.UserRole;
import de.toengi.cili.repository.AclEntryRepository;
import de.toengi.cili.repository.FolderRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.UserGroupMembershipRepository;
import de.toengi.cili.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AclService {
  public static final String USER_NOT_FOUND = "User not found: ";
  private final AclEntryRepository aclEntryRepository;
  private final UserRepository userRepository;
  private final UserGroupMembershipRepository membershipRepository;
  private final FolderRepository folderRepository;
  private final ResourceRepository resourceRepository;
  private final ObjectProvider<AclService> self;

  @Transactional(readOnly = true)
  public boolean hasPermission(Long actorUserId, Long resourceId, AclResourceType resourceType, AclPermission permission) {
    SecurityContext ctx = securityContext(actorUserId);
    if (ctx.isAdmin()) {
      return true;
    }
    List<Long> safeGroupIds = ctx.groupIds();
    Optional<Boolean> direct = resolveEntries(effectiveEntries(resourceType,
        resourceId,
        actorUserId,
        safeGroupIds), permission);
    return direct.orElseGet(() -> resolveViaFolderHierarchy(actorUserId,
        resourceId,
        resourceType,
        permission,
        safeGroupIds));
  }

  @Transactional(readOnly = true)
  public boolean hasAccessibleDescendant(Long actorUserId, Long folderId) {
    SecurityContext ctx = securityContext(actorUserId);
    if (ctx.isAdmin()) {
      return true;
    }
    List<Long> safeGroupIds = ctx.groupIds();
    List<Long> allowedFolderIds = aclEntryRepository.findAllowedFolderIds(actorUserId,
        safeGroupIds);
    if (allowedFolderIds.isEmpty()) {
      return false;
    }
    Folder folder = folderRepository.findById(folderId).orElse(null);
    if (folder == null) {
      return false;
    }
    return folderRepository.existsByIdInAndPathStartingWith(allowedFolderIds, folder.getPath());
  }

  // Optional.empty() = kein Filter nötig (Admin / globales READ ALLOW)
  // Optional.of(List.of(-1L)) = kein Zugang
  @Transactional(readOnly = true)
  public Optional<List<Long>> getAccessibleFolderIds(Long actorUserId) {
    SecurityContext ctx = securityContext(actorUserId);
    if (ctx.isAdmin()) {
      return Optional.empty();
    }
    List<Long> safeGroupIds = ctx.groupIds();
    List<AclEntry> globalEntries = aclEntryRepository.findGlobalEffectiveEntries(actorUserId,
        safeGroupIds);
    if (resolveEntries(globalEntries, AclPermission.READ).orElse(false)) {
      return Optional.empty();
    }
    Set<Long> result = new HashSet<>(aclEntryRepository.findAllowedFolderIds(actorUserId,
        safeGroupIds));
    for (Long fid : aclEntryRepository.findInheritableAllowedFolderIds(actorUserId, safeGroupIds)) {
      folderRepository.findById(fid)
          .ifPresent(folder -> folderRepository.findByPathStartingWith(folder.getPath())
              .forEach(child -> result.add(child.getId())));
    }
    return Optional.of(result.isEmpty() ? List.of(-1L) : new ArrayList<>(result));
  }

  @Transactional(readOnly = true)
  public Set<AclPermission> getEffectivePermissions(Long actorUserId, Long resourceId, AclResourceType resourceType) {
    Set<AclPermission> result = new HashSet<>();
    for (AclPermission permission : AclPermission.values()) {
      if (self.getObject().hasPermission(actorUserId, resourceId, resourceType, permission)) {
        result.add(permission);
      }
    }
    return result;
  }

  @Transactional(readOnly = true)
  public List<AclEntry> listGroupEntries(Long groupId) {
    return aclEntryRepository.findBySubjectTypeAndSubjectId(AclSubjectType.GROUP, groupId);
  }

  @Transactional(readOnly = true)
  public List<AclEntry> listFolderEntries(Long folderId) {
    return aclEntryRepository.findByResourceTypeAndResourceId(AclResourceType.FOLDER, folderId);
  }

  @Transactional
  public AclEntry createFolderEntry(Long folderId, CreateAclEntryRequest request) {
    AclEntry entry = AclEntry.builder()
        .subjectType(request.subjectType())
        .subjectId(request.subjectId())
        .resourceType(AclResourceType.FOLDER)
        .resourceId(folderId)
        .permission(request.permission())
        .grantType(request.grantType())
        .inheritable(request.inheritable())
        .build();
    AclEntry saved = aclEntryRepository.save(entry);
    log.info(
        "ACL-Eintrag erstellt: id={} folder={} subject={}/{} permission={} grant={} inheritable={}",
        saved.getId(),
        folderId,
        request.subjectType(),
        request.subjectId(),
        request.permission(),
        request.grantType(),
        request.inheritable());
    return saved;
  }

  @Transactional
  public void deleteEntry(Long entryId) {
    AclEntry entry = aclEntryRepository.findById(entryId)
        .orElseThrow(() -> new ResourceNotFoundException("AclEntry", entryId));
    aclEntryRepository.deleteById(entryId);
    log.info("ACL-Eintrag gelöscht: id={} subject={}/{} resource={}/{} permission={} grant={}",
        entryId,
        entry.getSubjectType(),
        entry.getSubjectId(),
        entry.getResourceType(),
        entry.getResourceId(),
        entry.getPermission(),
        entry.getGrantType());
  }

  @Transactional(readOnly = true)
  public Page<Folder> listAllFolders(int page, int size) {
    return folderRepository.findByTrashedFalseOrderByName(PageRequest.of(page, size));
  }

  @Transactional(readOnly = true)
  public boolean hasTestimonialsPermission(Long actorUserId, AclPermission permission) {
    SecurityContext ctx = securityContext(actorUserId);
    if (ctx.isAdmin()) {
      return true;
    }
    List<Long> safeGroupIds = ctx.groupIds();
    List<AclEntry> entries = effectiveEntries(AclResourceType.TESTIMONIALS,
        0L,
        actorUserId,
        safeGroupIds);
    return resolveEntries(entries, permission).orElse(false);
  }

  @Transactional(readOnly = true)
  public Set<AclPermission> getTestimonialsEffectivePermissions(Long actorUserId) {
    SecurityContext ctx = securityContext(actorUserId);
    if (ctx.isAdmin()) {
      return Set.of(AclPermission.READ, AclPermission.WRITE, AclPermission.DELETE);
    }
    List<Long> safeGroupIds = ctx.groupIds();
    List<AclEntry> entries = effectiveEntries(AclResourceType.TESTIMONIALS,
        0L,
        actorUserId,
        safeGroupIds);
    Set<AclPermission> result = new HashSet<>();
    for (AclPermission perm : List.of(AclPermission.READ,
        AclPermission.WRITE,
        AclPermission.DELETE)) {
      if (resolveEntries(entries, perm).orElse(false)) {
        result.add(perm);
      }
    }
    return result;
  }

  @Transactional
  public AclEntry createTestimonialsEntry(CreateTestimonialsAclEntryRequest request) {
    AclEntry saved = aclEntryRepository.save(AclEntry.builder()
        .subjectType(request.subjectType())
        .subjectId(request.subjectId())
        .resourceType(AclResourceType.TESTIMONIALS)
        .resourceId(0L)
        .permission(request.permission())
        .grantType(AclGrantType.ALLOW)
        .inheritable(false)
        .build());
    log.info("Testimonials ACL-Eintrag erstellt: id={} subject={}/{} permission={}",
        saved.getId(),
        request.subjectType(),
        request.subjectId(),
        request.permission());
    return saved;
  }

  @Transactional(readOnly = true)
  public boolean hasCollectionsPermission(Long actorUserId, AclPermission permission) {
    SecurityContext ctx = securityContext(actorUserId);
    if (ctx.isAdmin()) {
      return true;
    }
    List<Long> safeGroupIds = ctx.groupIds();
    List<AclEntry> entries = effectiveEntries(AclResourceType.COLLECTIONS,
        0L,
        actorUserId,
        safeGroupIds);
    return resolveEntries(entries, permission).orElse(false);
  }

  @Transactional(readOnly = true)
  public Set<AclPermission> getCollectionsEffectivePermissions(Long actorUserId) {
    SecurityContext ctx = securityContext(actorUserId);
    if (ctx.isAdmin()) {
      return Set.of(AclPermission.MANAGE_TEMPLATES);
    }
    List<Long> safeGroupIds = ctx.groupIds();
    List<AclEntry> entries = effectiveEntries(AclResourceType.COLLECTIONS,
        0L,
        actorUserId,
        safeGroupIds);
    Set<AclPermission> result = new HashSet<>();
    if (resolveEntries(entries, AclPermission.MANAGE_TEMPLATES).orElse(false)) {
      result.add(AclPermission.MANAGE_TEMPLATES);
    }
    return result;
  }

  @Transactional
  public AclEntry createCollectionsEntry(CreateCollectionsAclEntryRequest request) {
    AclEntry saved = aclEntryRepository.save(AclEntry.builder()
        .subjectType(request.subjectType())
        .subjectId(request.subjectId())
        .resourceType(AclResourceType.COLLECTIONS)
        .resourceId(0L)
        .permission(request.permission())
        .grantType(AclGrantType.ALLOW)
        .inheritable(false)
        .build());
    log.info("Collections ACL-Eintrag erstellt: id={} subject={}/{} permission={}",
        saved.getId(),
        request.subjectType(),
        request.subjectId(),
        request.permission());
    return saved;
  }

  // "/1/4/12/" → [4L, 1L]  (exclude self=last, reverse for closest-first)
  List<Long> parseAncestorIds(String path) {
    if (path == null || path.isBlank() || path.equals("/")) {
      return List.of();
    }
    String[] parts = path.split("/");
    List<Long> ids = new ArrayList<>();
    for (String part : parts) {
      if (!part.isBlank()) {
        ids.add(Long.parseLong(part));
      }
    }
    if (!ids.isEmpty()) {
      ids.removeLast();
    }
    Collections.reverse(ids);
    return Collections.unmodifiableList(ids);
  }

  private boolean resolveViaFolderHierarchy(Long actorUserId, Long resourceId, AclResourceType resourceType, AclPermission permission, List<Long> safeGroupIds) {
    Long folderToWalk = null;
    if (resourceType == AclResourceType.FOLDER) {
      folderToWalk = resourceId;
    } else if (resourceId != null) {
      var res = resourceRepository.findById(resourceId).orElse(null);
      if (res == null) {
        return false;
      }
      folderToWalk = res.getFolderId();
      if (folderToWalk == null) {
        return (permission == AclPermission.READ || permission == AclPermission.DOWNLOAD)
            && self.getObject().hasTestimonialsPermission(actorUserId, AclPermission.READ);
      }
      Optional<Boolean> folderDirect = resolveEntries(effectiveEntries(AclResourceType.FOLDER,
          folderToWalk,
          actorUserId,
          safeGroupIds), permission);
      if (folderDirect.isPresent()) {
        return folderDirect.get();
      }
    }
    if (folderToWalk != null) {
      Optional<Boolean> ancestor = resolveAncestors(folderToWalk,
          actorUserId,
          safeGroupIds,
          permission);
      if (ancestor.isPresent()) {
        return ancestor.get();
      }
    }
    return resolveEntries(aclEntryRepository.findGlobalEffectiveEntries(actorUserId, safeGroupIds),
        permission).orElse(false);
  }

  private Optional<Boolean> resolveAncestors(Long folderId, Long actorUserId, List<Long> groupIds, AclPermission permission) {
    Optional<Folder> folderOpt = folderRepository.findById(folderId);
    if (folderOpt.isEmpty()) {
      return Optional.empty();
    }
    for (Long ancestorId : parseAncestorIds(folderOpt.get().getPath())) {
      Optional<Boolean> result = resolveEntries(aclEntryRepository.findInheritableEffectiveEntries(
          AclResourceType.FOLDER,
          ancestorId,
          actorUserId,
          groupIds), permission);
      if (result.isPresent()) {
        return result;
      }
    }
    return Optional.empty();
  }

  // USER-DENY > USER-ALLOW > GROUP-DENY > GROUP-ALLOW; empty = no decision at this level
  private Optional<Boolean> resolveEntries(List<AclEntry> entries, AclPermission permission) {
    List<AclEntry> matching = entries.stream()
        .filter(e -> e.getPermission() == permission)
        .toList();
    if (matching.isEmpty()) {
      return Optional.empty();
    }
    if (matching.stream()
        .anyMatch(e -> e.getSubjectType() == AclSubjectType.USER
            && e.getGrantType() == AclGrantType.DENY)) {
      return Optional.of(false);
    }
    if (matching.stream()
        .anyMatch(e -> e.getSubjectType() == AclSubjectType.USER
            && e.getGrantType() == AclGrantType.ALLOW)) {
      return Optional.of(true);
    }
    if (matching.stream()
        .anyMatch(e -> e.getSubjectType() == AclSubjectType.GROUP
            && e.getGrantType() == AclGrantType.DENY)) {
      return Optional.of(false);
    }
    if (matching.stream()
        .anyMatch(e -> e.getSubjectType() == AclSubjectType.GROUP
            && e.getGrantType() == AclGrantType.ALLOW)) {
      return Optional.of(true);
    }
    return Optional.empty();
  }

  private SecurityContext securityContext(Long actorUserId) {
    User user = userRepository.findById(actorUserId)
        .orElseThrow(() -> new NoSuchElementException(USER_NOT_FOUND + actorUserId));
    List<Long> groupIds = membershipRepository.findByUserId(actorUserId)
        .stream()
        .map(m -> m.getId().getGroupId())
        .toList();
    return new SecurityContext(user, groupIds.isEmpty() ? List.of(-1L) : groupIds);
  }

  private List<AclEntry> effectiveEntries(AclResourceType type, Long resourceId, Long actorUserId, List<Long> groupIds) {
    return aclEntryRepository.findEffectiveEntries(type, resourceId, actorUserId, groupIds);
  }

  private record SecurityContext(User user, List<Long> groupIds) {
    boolean isAdmin() {
      return user.getRole() == UserRole.ADMIN;
    }
  }
}