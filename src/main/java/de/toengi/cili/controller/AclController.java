package de.toengi.cili.controller;

import de.toengi.cili.dto.acl.AclEntryDto;
import de.toengi.cili.dto.acl.CreateAclEntryRequest;
import de.toengi.cili.dto.acl.CreateCollectionsAclEntryRequest;
import de.toengi.cili.dto.acl.CreateTestimonialsAclEntryRequest;
import de.toengi.cili.dto.acl.EffectivePermissionsResponse;
import de.toengi.cili.dto.common.PageResponse;
import de.toengi.cili.mapper.AclMapper;
import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.AclResourceType;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.AclService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/acl")
@RequiredArgsConstructor
public class AclController {

    private final AclService aclService;
    private final AclMapper aclMapper;

    public record FolderItem(Long id, String name, String path) {}

    @GetMapping("/folders")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<FolderItem> listAllFolders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var folders = aclService.listAllFolders(page, size);
        var items = folders.getContent().stream()
                .map(f -> new FolderItem(f.getId(), f.getName(), f.getPath()))
                .toList();
        return new PageResponse<>(items, page, size, folders.getTotalElements(), folders.getTotalPages());
    }

    @GetMapping("/groups/{groupId}/entries")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AclEntryDto> listGroupEntries(@PathVariable Long groupId) {
        return aclService.listGroupEntries(groupId).stream().map(aclMapper::toDto).toList();
    }

    @GetMapping("/folders/{folderId}/entries")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AclEntryDto> listFolderEntries(@PathVariable Long folderId) {
        return aclService.listFolderEntries(folderId).stream().map(aclMapper::toDto).toList();
    }

    @PostMapping("/folders/{folderId}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AclEntryDto createFolderEntry(@PathVariable Long folderId,
                                          @Valid @RequestBody CreateAclEntryRequest request) {
        return aclMapper.toDto(aclService.createFolderEntry(folderId, request));
    }

    @DeleteMapping("/entries/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteEntry(@PathVariable Long entryId) {
        aclService.deleteEntry(entryId);
    }

    @GetMapping("/folders/{folderId}/effective-permissions")
    public EffectivePermissionsResponse getMyEffectivePermissions(
            @PathVariable Long folderId,
            @AuthenticationPrincipal CiliUserDetails userDetails) {
        Set<AclPermission> permissions = aclService.getEffectivePermissions(
                userDetails.getUserId(), folderId, AclResourceType.FOLDER);
        return new EffectivePermissionsResponse(
                userDetails.getUserId(), AclResourceType.FOLDER, folderId, permissions);
    }

    @GetMapping("/folders/{folderId}/effective-permissions/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public EffectivePermissionsResponse getUserEffectivePermissions(
            @PathVariable Long folderId,
            @PathVariable Long userId) {
        Set<AclPermission> permissions = aclService.getEffectivePermissions(
                userId, folderId, AclResourceType.FOLDER);
        return new EffectivePermissionsResponse(userId, AclResourceType.FOLDER, folderId, permissions);
    }

    @GetMapping("/testimonials/effective-permissions")
    public EffectivePermissionsResponse getMyTestimonialsPermissions(
            @AuthenticationPrincipal CiliUserDetails userDetails) {
        Set<AclPermission> permissions = aclService.getTestimonialsEffectivePermissions(
                userDetails.getUserId());
        return new EffectivePermissionsResponse(
                userDetails.getUserId(), AclResourceType.TESTIMONIALS, 0L, permissions);
    }

    @PostMapping("/testimonials/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AclEntryDto createTestimonialsEntry(
            @Valid @RequestBody CreateTestimonialsAclEntryRequest request) {
        return aclMapper.toDto(aclService.createTestimonialsEntry(request));
    }

    @GetMapping("/collections/effective-permissions")
    public EffectivePermissionsResponse getMyCollectionsPermissions(
            @AuthenticationPrincipal CiliUserDetails userDetails) {
        Set<AclPermission> permissions = aclService.getCollectionsEffectivePermissions(
                userDetails.getUserId());
        return new EffectivePermissionsResponse(
                userDetails.getUserId(), AclResourceType.COLLECTIONS, 0L, permissions);
    }

    @PostMapping("/collections/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AclEntryDto createCollectionsEntry(
            @Valid @RequestBody CreateCollectionsAclEntryRequest request) {
        return aclMapper.toDto(aclService.createCollectionsEntry(request));
    }
}
