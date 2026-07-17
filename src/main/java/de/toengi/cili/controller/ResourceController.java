package de.toengi.cili.controller;

import de.toengi.cili.dto.resource.*;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.ResourceService;
import de.toengi.cili.service.TextEditorService;
import de.toengi.cili.service.storage.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ResourceController {

    private final ResourceService resourceService;
    private final TextEditorService textEditorService;
    private final StorageService storageService;

    // --- Resource listing per folder ---

    @GetMapping("/api/folders/{folderId}/resources")
    public List<ResourceDto> listByFolder(@PathVariable Long folderId,
                                          @AuthenticationPrincipal CiliUserDetails user) {
        return resourceService.listByFolder(folderId, user.getUserId());
    }

    @PatchMapping("/api/folders/{folderId}/resources/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@PathVariable Long folderId,
                        @Valid @RequestBody ReorderResourcesRequest request,
                        @AuthenticationPrincipal CiliUserDetails user) {
        resourceService.reorder(folderId, request.resourceIds(), user.getUserId());
    }

    @PatchMapping("/api/resources/{id}/move")
    public ResourceDto moveResource(@PathVariable Long id,
                                    @RequestParam Long newFolderId,
                                    @AuthenticationPrincipal CiliUserDetails user) {
        ResourceDto dto = resourceService.moveResource(id, newFolderId, user.getUserId());
        log.info("[user:{}] Datei verschoben: \"{}\" (id={}) → Ordner {}", user.getUsername(), dto.originalName(), id, newFolderId);
        return dto;
    }

    // --- Resource CRUD ---

    @GetMapping("/api/resources/{id}")
    public ResourceDto getResource(@PathVariable Long id,
                                   @AuthenticationPrincipal CiliUserDetails user) {
        return resourceService.getById(id, user.getUserId());
    }

    @GetMapping("/api/resources/{id}/download")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable Long id,
            @AuthenticationPrincipal CiliUserDetails user) {
        Resource resource = resourceService.download(id, user.getUserId());
        log.info("[user:{}] Datei heruntergeladen: \"{}\" (id={}, {} MB)",
            user.getUsername(), resource.getOriginalName(), id, resource.getSize() / 1024 / 1024);
        StreamingResponseBody body = outputStream -> {
            try (InputStream stream = storageService.retrieve(resource.getStoredName())) {
                stream.transferTo(outputStream);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(resource.getOriginalName())
                                .build().toString())
                .contentType(MediaType.parseMediaType(resource.getMimeType()))
                .contentLength(resource.getSize())
                .body(body);
    }

    @PutMapping("/api/resources/{id}")
    public ResourceDto renameResource(@PathVariable Long id,
                                      @Valid @RequestBody UpdateResourceRequest request,
                                      @AuthenticationPrincipal CiliUserDetails user) {
        ResourceDto dto = resourceService.rename(id, request.originalName(), user.getUserId());
        log.info("[user:{}] Datei umbenannt: id={} → \"{}\"", user.getUsername(), id, dto.originalName());
        return dto;
    }

    @DeleteMapping("/api/resources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResource(@PathVariable Long id,
                               @AuthenticationPrincipal CiliUserDetails user) {
        resourceService.delete(id, user.getUserId());
        log.info("[user:{}] Datei gelöscht: id={}", user.getUsername(), id);
    }

    // --- Metadata ---

    @PutMapping("/api/resources/{id}/metadata")
    public MetadataDto updateMetadata(@PathVariable Long id,
                                      @Valid @RequestBody UpdateMetadataRequest request,
                                      @AuthenticationPrincipal CiliUserDetails user) {
        MetadataDto dto = resourceService.updateMetadata(id, request, user.getUserId());
        log.info("[user:{}] Metadaten aktualisiert: id={}", user.getUsername(), id);
        return dto;
    }

    // --- Favorites ---

    @GetMapping("/api/resources/favorites")
    public List<ResourceDto> getFavorites(@AuthenticationPrincipal CiliUserDetails user) {
        return resourceService.getFavorites(user.getUserId());
    }

    @PostMapping("/api/resources/{id}/favorite")
    @ResponseStatus(HttpStatus.CREATED)
    public void addFavorite(@PathVariable Long id,
                            @AuthenticationPrincipal CiliUserDetails user) {
        resourceService.addFavorite(id, user.getUserId());
    }

    @DeleteMapping("/api/resources/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@PathVariable Long id,
                               @AuthenticationPrincipal CiliUserDetails user) {
        resourceService.removeFavorite(id, user.getUserId());
    }

    // --- Text editor ---

    @GetMapping("/api/resources/{id}/content")
    public String getContent(@PathVariable Long id,
                             @AuthenticationPrincipal CiliUserDetails user) {
        return textEditorService.getContent(id, user.getUserId());
    }

    @PutMapping("/api/resources/{id}/content")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveContent(@PathVariable Long id,
                            @RequestBody String content,
                            @AuthenticationPrincipal CiliUserDetails user) {
        textEditorService.saveContent(id, content, user.getUserId());
        log.info("[user:{}] Textdatei gespeichert: id={}", user.getUsername(), id);
    }
}
