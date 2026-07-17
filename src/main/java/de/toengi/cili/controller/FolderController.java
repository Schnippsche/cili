package de.toengi.cili.controller;

import de.toengi.cili.dto.folder.*;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
@Slf4j
public class FolderController {

    private final FolderService folderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderDto createFolder(@Valid @RequestBody CreateFolderRequest request,
                                   @AuthenticationPrincipal CiliUserDetails userDetails) {
        FolderDto dto = folderService.createFolder(request, userDetails.getUserId());
        log.info("[user:{}] Ordner erstellt: \"{}\" (id={}, parent={})",
            userDetails.getUsername(), dto.name(), dto.id(), dto.parentId());
        return dto;
    }

    @GetMapping("/root")
    public List<FolderDto> listRootFolders(@AuthenticationPrincipal CiliUserDetails userDetails) {
        return folderService.listRootFolders(userDetails.getUserId());
    }

    @GetMapping("/trash")
    @PreAuthorize("hasRole('ADMIN')")
    public List<FolderDto> listTrash() {
        return folderService.listTrash();
    }

    @GetMapping("/favorites")
    public List<FolderDto> getFavorites(@AuthenticationPrincipal CiliUserDetails userDetails) {
        return folderService.getFavorites(userDetails.getUserId());
    }

    @GetMapping("/{id}")
    public FolderDto getFolder(@PathVariable Long id) {
        return folderService.getFolder(id);
    }

    @GetMapping("/{id}/children")
    public List<FolderDto> listChildren(@PathVariable Long id,
                                        @AuthenticationPrincipal CiliUserDetails userDetails) {
        return folderService.listChildren(id, userDetails.getUserId());
    }

    @GetMapping("/{id}/breadcrumb")
    public List<BreadcrumbItemDto> getBreadcrumb(@PathVariable Long id,
                                                  @AuthenticationPrincipal CiliUserDetails userDetails) {
        return folderService.getBreadcrumb(id, userDetails.getUserId());
    }

    @PatchMapping("/{id}")
    public FolderDto updateFolder(@PathVariable Long id,
                                   @Valid @RequestBody UpdateFolderRequest request,
                                   @AuthenticationPrincipal CiliUserDetails user) {
        FolderDto dto = folderService.updateFolder(id, request);
        log.info("[user:{}] Ordner umbenannt: id={} → \"{}\"", user.getUsername(), id, dto.name());
        return dto;
    }

    @PutMapping("/{id}/move")
    public FolderDto moveFolder(@PathVariable Long id, @RequestParam Long newParentId,
                                @AuthenticationPrincipal CiliUserDetails user) {
        FolderDto dto = folderService.moveFolder(id, newParentId);
        log.info("[user:{}] Ordner verschoben: \"{}\" (id={}) → parent={}", user.getUsername(), dto.name(), id, newParentId);
        return dto;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trashFolder(@PathVariable Long id, @AuthenticationPrincipal CiliUserDetails user) {
        folderService.trashFolder(id);
        log.info("[user:{}] Ordner in Papierkorb: id={}", user.getUsername(), id);
    }

    @PostMapping("/{id}/restore")
    public FolderDto restoreFolder(@PathVariable Long id, @AuthenticationPrincipal CiliUserDetails user) {
        FolderDto dto = folderService.restoreFolder(id);
        log.info("[user:{}] Ordner wiederhergestellt: \"{}\" (id={})", user.getUsername(), dto.name(), id);
        return dto;
    }

    @DeleteMapping("/{id}/purge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purgeFolder(@PathVariable Long id, @AuthenticationPrincipal CiliUserDetails user) {
        folderService.purgeFolder(id);
        log.info("[user:{}] Ordner endgültig gelöscht: id={}", user.getUsername(), id);
    }

    @PostMapping("/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addFavorite(@PathVariable Long id,
                            @AuthenticationPrincipal CiliUserDetails userDetails) {
        folderService.addFavorite(id, userDetails.getUserId());
    }

    @DeleteMapping("/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@PathVariable Long id,
                               @AuthenticationPrincipal CiliUserDetails userDetails) {
        folderService.removeFavorite(id, userDetails.getUserId());
    }
}
