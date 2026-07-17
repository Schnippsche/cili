package de.toengi.cili.service;

import de.toengi.cili.dto.folder.BreadcrumbItemDto;
import de.toengi.cili.dto.folder.CreateFolderRequest;
import de.toengi.cili.dto.folder.FolderDto;
import de.toengi.cili.dto.folder.UpdateFolderRequest;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.mapper.FolderMapper;
import de.toengi.cili.model.entity.Folder;
import de.toengi.cili.model.entity.FolderFavorite;
import de.toengi.cili.model.entity.FolderFavoriteId;
import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.AclResourceType;
import de.toengi.cili.repository.AclEntryRepository;
import de.toengi.cili.repository.FolderFavoriteRepository;
import de.toengi.cili.repository.FolderRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolderService {
  public static final String FOLDER = "Folder";
  private static final String PATH_SEPARATOR = "/";
  private final FolderRepository folderRepository;
  private final FolderFavoriteRepository favoriteRepository;
  private final AclEntryRepository aclEntryRepository;
  private final AclService aclService;
  private final FolderMapper folderMapper;

  @Transactional
  @PreAuthorize(
      "(#request.parentId != null and hasPermission(#request.parentId, 'FOLDER', 'WRITE'))"
          + " or (#request.parentId == null and hasRole('ADMIN'))")
  public FolderDto createFolder(CreateFolderRequest request, Long createdByUserId) {
    String parentPath = PATH_SEPARATOR;
    if (request.parentId() != null) {
      Folder parent = folderRepository.findById(request.parentId())
          .orElseThrow(() -> new ResourceNotFoundException(FOLDER, request.parentId()));
      parentPath = parent.getPath();
    }
    Folder folder = folderRepository.save(Folder.builder()
        .name(request.name())
        .parentId(request.parentId())
        .description(request.description())
        .createdBy(createdByUserId)
        .build());
    folder.setPath(parentPath + folder.getId() + PATH_SEPARATOR);
    FolderDto result = folderMapper.toDto(folderRepository.save(folder));
    log.info("Ordner erstellt: user={} folder={} name='{}' parentId={}", createdByUserId, result.id(), result.name(), request.parentId());
    return result;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasPermission(#folderId, 'FOLDER', 'READ')")
  public FolderDto getFolder(Long folderId) {
    return folderMapper.toDto(folderRepository.findById(folderId)
        .orElseThrow(() -> new ResourceNotFoundException(FOLDER, folderId)));
  }

  @Transactional(readOnly = true)
  public List<FolderDto> listChildren(Long folderId, Long actorUserId) {
    folderRepository.findById(folderId)
        .orElseThrow(() -> new ResourceNotFoundException(FOLDER, folderId));
    boolean hasRead = aclService.hasPermission(actorUserId,
        folderId,
        AclResourceType.FOLDER,
        AclPermission.READ);
    if (!hasRead && !aclService.hasAccessibleDescendant(actorUserId, folderId)) {
      throw new AccessDeniedException("No access to folder " + folderId);
    }
    List<Folder> children = folderRepository.findByParentIdAndTrashedFalse(folderId);
    if (hasRead) {
      return children.stream().map(folderMapper::toDto).toList();
    }
    return children.stream()
        .filter(c -> aclService.hasPermission(actorUserId,
            c.getId(),
            AclResourceType.FOLDER,
            AclPermission.READ) || aclService.hasAccessibleDescendant(actorUserId, c.getId()))
        .map(folderMapper::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FolderDto> listRootFolders(Long actorUserId) {
    return folderRepository.findRootFolders()
        .stream()
        .filter(f -> aclService.hasPermission(actorUserId,
            f.getId(),
            AclResourceType.FOLDER,
            AclPermission.READ) || aclService.hasAccessibleDescendant(actorUserId, f.getId()))
        .map(folderMapper::toDto)
        .toList();
  }

  @Transactional
  @PreAuthorize("hasPermission(#folderId, 'FOLDER', 'WRITE')")
  public FolderDto updateFolder(Long folderId, UpdateFolderRequest request) {
    Folder folder = folderRepository.findById(folderId)
        .orElseThrow(() -> new ResourceNotFoundException(FOLDER, folderId));
    String oldName = folder.getName();
    folder.setName(request.name());
    folder.setDescription(request.description());
    FolderDto result = folderMapper.toDto(folderRepository.save(folder));
    log.info("Ordner umbenannt: folder={} '{}' -> '{}'", folderId, oldName, request.name());
    return result;
  }

  @Transactional
  @PreAuthorize("hasPermission(#folderId, 'FOLDER', 'DELETE') and hasPermission(#newParentId, 'FOLDER', 'WRITE')")
  public FolderDto moveFolder(Long folderId, Long newParentId) {
    Folder folder = folderRepository.findById(folderId)
        .orElseThrow(() -> new ResourceNotFoundException(FOLDER, folderId));
    if (folder.isTrashed()) {
      throw new IllegalStateException("Cannot move a trashed folder");
    }
    Folder newParent = folderRepository.findById(newParentId)
        .orElseThrow(() -> new ResourceNotFoundException(FOLDER, newParentId));
    if (newParent.getPath().startsWith(folder.getPath())) {
      throw new IllegalArgumentException("Cannot move folder: would create a cycle");
    }
    String oldPrefix = folder.getPath();
    String newFolderPath = newParent.getPath() + folderId + PATH_SEPARATOR;
    List<Folder> affected = folderRepository.findByPathStartingWith(oldPrefix);
    for (Folder f : affected) {
      if (f.getId().equals(folderId)) {
        f.setParentId(newParentId);
      }
      f.setPath(newFolderPath + f.getPath().substring(oldPrefix.length()));
    }
    folderRepository.saveAll(affected);
    log.info("Ordner verschoben: folder={} name='{}' -> parentId={}", folderId, folder.getName(), newParentId);
    return folderMapper.toDto(folder);
  }

  @Transactional
  @PreAuthorize("hasPermission(#folderId, 'FOLDER', 'DELETE')")
  public void trashFolder(Long folderId) {
    Folder folder = folderRepository.findById(folderId)
        .orElseThrow(() -> new ResourceNotFoundException(FOLDER, folderId));
    LocalDateTime now = LocalDateTime.now();
    List<Folder> affected = folderRepository.findByPathStartingWith(folder.getPath());
    for (Folder f : affected) {
      f.setTrashed(true);
      f.setTrashedAt(now);
    }
    folderRepository.saveAll(affected);
    log.info("Ordner in Papierkorb: folder={} name='{}' (betroffen: {} Ordner)", folderId, folder.getName(), affected.size());
  }

  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public FolderDto restoreFolder(Long folderId) {
    Folder folder = folderRepository.findById(folderId)
        .orElseThrow(() -> new ResourceNotFoundException(FOLDER, folderId));
    List<Folder> affected = folderRepository.findByPathStartingWith(folder.getPath());
    for (Folder f : affected) {
      f.setTrashed(false);
      f.setTrashedAt(null);
    }
    folderRepository.saveAll(affected);
    log.info("Ordner wiederhergestellt: folder={} name='{}' (betroffen: {} Ordner)", folderId, folder.getName(), affected.size());
    return folderMapper.toDto(folder);
  }

  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public void purgeFolder(Long folderId) {
    Folder folder = folderRepository.findById(folderId)
        .orElseThrow(() -> new ResourceNotFoundException(FOLDER, folderId));
    if (!folder.isTrashed()) {
      throw new IllegalStateException("Folder must be trashed before purging");
    }
    List<Long> affectedIds = folderRepository.findByPathStartingWith(folder.getPath())
        .stream()
        .map(Folder::getId)
        .toList();
    aclEntryRepository.deleteByResourceTypeAndResourceIdIn(AclResourceType.FOLDER, affectedIds);
    folderRepository.delete(folder);
    log.info("Ordner endgültig gelöscht: folder={} name='{}' (betroffen: {} Ordner)", folderId, folder.getName(), affectedIds.size());
  }

  @Transactional(readOnly = true)
  public List<BreadcrumbItemDto> getBreadcrumb(Long folderId, Long actorUserId) {
    Folder folder = folderRepository.findById(folderId)
        .orElseThrow(() -> new ResourceNotFoundException(FOLDER, folderId));
    boolean hasRead = aclService.hasPermission(actorUserId,
        folderId,
        AclResourceType.FOLDER,
        AclPermission.READ);
    if (!hasRead && !aclService.hasAccessibleDescendant(actorUserId, folderId)) {
      throw new AccessDeniedException("No access to folder " + folderId);
    }
    List<Long> ids = parsePathIds(folder.getPath());
    Map<Long, Folder> byId = folderRepository.findAllById(ids)
        .stream()
        .collect(Collectors.toMap(Folder::getId, f -> f));
    return ids.stream()
        .map(byId::get)
        .filter(Objects::nonNull)
        .map(folderMapper::toBreadcrumbItem)
        .toList();
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMIN')")
  public List<FolderDto> listTrash() {
    return folderRepository.findByTrashedTrue().stream().map(folderMapper::toDto).toList();
  }

  @Transactional
  public void addFavorite(Long folderId, Long userId) {
    FolderFavoriteId id = new FolderFavoriteId(userId, folderId);
    if (!favoriteRepository.existsById(id)) {
      Folder folder = folderRepository.findById(folderId)
          .orElseThrow(() -> new ResourceNotFoundException(FOLDER, folderId));
      if (folder.isTrashed()) {
        throw new IllegalStateException("Cannot favorite a trashed folder");
      }
      favoriteRepository.save(new FolderFavorite(id, null));
    }
  }

  @Transactional
  public void removeFavorite(Long folderId, Long userId) {
    favoriteRepository.deleteById(new FolderFavoriteId(userId, folderId));
  }

  @Transactional(readOnly = true)
  public List<FolderDto> getFavorites(Long userId) {
    List<Long> ids = favoriteRepository.findFolderIdsByUserId(userId);
    if (ids.isEmpty()) {
      return List.of();
    }
    return folderRepository.findAllById(ids)
        .stream()
        .filter(f -> !f.isTrashed())
        .map(folderMapper::toDto)
        .toList();
  }

  List<Long> parsePathIds(String path) {
    if (path == null || path.isBlank() || path.equals(PATH_SEPARATOR)) {
      return List.of();
    }
    List<Long> ids = new ArrayList<>();
    for (String part : path.split(PATH_SEPARATOR)) {
      if (!part.isBlank()) {
        ids.add(Long.parseLong(part));
      }
    }
    return Collections.unmodifiableList(ids);
  }
}
