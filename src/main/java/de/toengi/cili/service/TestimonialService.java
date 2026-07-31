package de.toengi.cili.service;

import de.toengi.cili.dto.testimonial.*;
import de.toengi.cili.event.ResourceUploadedEvent;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.Testimonial;
import de.toengi.cili.model.entity.Thumbnail;
import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.StorageType;
import de.toengi.cili.model.enums.UserRole;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.TestimonialRepository;
import de.toengi.cili.repository.ThumbnailRepository;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.storage.StorageService;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestimonialService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );
    private final TestimonialRepository repository;
    private final ResourceRepository resourceRepository;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;
    private final AclService aclService;
    private final ThumbnailService thumbnailService;
    private final ThumbnailRepository thumbnailRepository;

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    // "source" bleibt als String-Filter-Parameter erhalten (statt auf zwei Boolean-Params
    // umgestellt zu werden), da scripts/telegram_import.py als externer API-Client weiterhin
    // exakt "Mensch"/"Tier" sendet. Semantik hat sich geändert: früher exakter Spaltenvergleich,
    // jetzt "hat dieses Flag gesetzt" (inklusiv — ein Bericht mit beiden Flags erscheint unter
    // beiden Filterwerten). Die Validierung MUSS vor der q-Verzweigung erfolgen, sonst würde ein
    // unbekannter source-Wert (z.B. veraltetes "Menschen") in Kombination mit einer Suchanfrage q
    // ungefiltert an searchLike() durchgereicht statt eine leere Seite zu liefern.
    public Page<TestimonialDto> list(String q, String source, Pageable pageable) {
        CiliUserDetails user = currentUser();
        if (!aclService.hasTestimonialsPermission(user.getUserId(), AclPermission.READ)) {
            throw new AccessDeniedException("Kein Zugriff auf Erfahrungsberichte");
        }
        if (source != null && !source.isBlank()
                && !"Mensch".equals(source) && !"Tier".equals(source)) {
            log.debug("Erfahrungsberichte-Filter: unbekannter source-Wert '{}' -> leere Seite", source);
            return Page.empty(pageable);
        }
        if (q != null && !q.isBlank()) {
            log.info("Erfahrungsberichte-Suche: user='{}' query='{}'", user.getUsername(), q.trim());
            return repository.searchLike(parseTerms(q), source, pageable).map(this::toDto);
        }
        if ("Mensch".equals(source)) return repository.findByIsHumanTrueOrderByCreatedAtDesc(pageable).map(this::toDto);
        if ("Tier".equals(source))   return repository.findByIsAnimalTrueOrderByCreatedAtDesc(pageable).map(this::toDto);
        return repository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public TestimonialDto get(Long id) {
        CiliUserDetails user = currentUser();
        if (!aclService.hasTestimonialsPermission(user.getUserId(), AclPermission.READ)) {
            throw new AccessDeniedException("Kein Zugriff auf Erfahrungsberichte");
        }
        return toDto(findOrThrow(id));
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public TestimonialDto create(CreateTestimonialRequest req, List<MultipartFile> images) {
        CiliUserDetails user = currentUser();
        if (!aclService.hasTestimonialsPermission(user.getUserId(), AclPermission.WRITE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Erstellen");
        }
        validate(req.authorName(), req.text());
        validateCategory(req.human(), req.animal());
        Testimonial t = Testimonial.builder()
            .authorName(req.authorName().trim())
            .tags(req.tags() != null && !req.tags().isBlank() ? req.tags().trim() : null)
            .text(req.text().trim())
            .isHuman(req.human())
            .isAnimal(req.animal())
            .userId(user.getUserId())
            .createdAt(req.createdAt())
            .build();
        t = repository.save(t);
        storeImages(t.getId(), images, user.getUserId());
        return toDto(t);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public TestimonialDto update(Long id, UpdateTestimonialRequest req,
                                  List<MultipartFile> newImages, List<Long> deleteResourceIds) {
        CiliUserDetails user = currentUser();
        if (!aclService.hasTestimonialsPermission(user.getUserId(), AclPermission.WRITE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Bearbeiten");
        }
        validate(req.authorName(), req.text());
        validateCategory(req.human(), req.animal());
        Testimonial t = findOrThrow(id);
        checkOwnerOrAdmin(t);
        t.setAuthorName(req.authorName().trim());
        t.setTags(req.tags() != null && !req.tags().isBlank() ? req.tags().trim() : null);
        t.setText(req.text().trim());
        t.setHuman(req.human());
        t.setAnimal(req.animal());
        if (deleteResourceIds != null) {
            deleteResourceIds.forEach(resourceId ->
                resourceRepository.findById(resourceId)
                    .filter(r -> id.equals(r.getTestimonialId()))
                    .ifPresent(r -> {
                        deleteResourceFile(r);
                        resourceRepository.delete(r);
                    })
            );
        }
        storeImages(id, newImages, user.getUserId());
        return toDto(repository.save(t));
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void delete(Long id) {
        CiliUserDetails user = currentUser();
        if (!aclService.hasTestimonialsPermission(user.getUserId(), AclPermission.DELETE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Löschen");
        }
        Testimonial t = findOrThrow(id);
        checkOwnerOrAdmin(t);
        resourceRepository.findByTestimonialIdOrderByCreatedAtAsc(id)
            .forEach(this::deleteResourceFile);
        repository.delete(t); // cascades resource rows via DB FK
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void deleteAttachment(Long testimonialId, Long resourceId) {
        CiliUserDetails user = currentUser();
        if (!aclService.hasTestimonialsPermission(user.getUserId(), AclPermission.WRITE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Bearbeiten");
        }
        Testimonial t = findOrThrow(testimonialId);
        checkOwnerOrAdmin(t);
        resourceRepository.findById(resourceId)
            .filter(r -> testimonialId.equals(r.getTestimonialId()))
            .ifPresentOrElse(r -> {
                deleteResourceFile(r);
                resourceRepository.delete(r);
            }, () -> { throw new ResourceNotFoundException("Resource", resourceId); });
    }

    @Transactional(readOnly = true)
    public void assertPublicAttachment(Long testimonialId, Long resourceId) {
        repository.findById(testimonialId)
            .orElseThrow(() -> new ResourceNotFoundException("Testimonial", testimonialId));

        Resource resource = resourceRepository.findById(resourceId)
            .orElseThrow(() -> new ResourceNotFoundException("Resource", resourceId));

        if (!Objects.equals(resource.getTestimonialId(), testimonialId)) {
            throw new AccessDeniedException("Resource does not belong to testimonial");
        }
    }

    private void storeImages(Long testimonialId, List<MultipartFile> files, Long uploaderId) {
        if (files == null || files.isEmpty()) return;
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String mime = file.getContentType();
            if (mime == null || !ALLOWED_IMAGE_TYPES.contains(mime)) {
                throw new CiliException("Nicht erlaubter Dateityp: " + mime, HttpStatus.BAD_REQUEST);
            }
            try {
                String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image";
                InputStream dataStream;
                long dataSize;

                if ("image/bmp".equals(mime)) {
                    // BMP → JPEG konvertieren; JPEG-Encoder unterstützt kein Alpha
                    byte[] jpeg = convertToJpeg(file.getInputStream());
                    dataStream = new ByteArrayInputStream(jpeg);
                    dataSize   = jpeg.length;
                    mime = "image/jpeg";
                    if (originalName.toLowerCase().endsWith(".bmp")) {
                        originalName = originalName.substring(0, originalName.length() - 4) + ".jpg";
                    }
                    log.debug("BMP-Bild '{}' für Testimonial {} als JPEG gespeichert ({} Bytes)", originalName, testimonialId, dataSize);
                } else {
                    dataStream = file.getInputStream();
                    dataSize   = file.getSize();
                }

                String storedName = storageService.store(dataStream, dataSize);
                Resource resource = Resource.builder()
                    .folderId(null)
                    .testimonialId(testimonialId)
                    .originalName(originalName)
                    .storedName(storedName)
                    .mimeType(mime)
                    .size(dataSize)
                    .uploaderId(uploaderId)
                    .storageType(StorageType.LOCAL)
                    .build();
                resource = resourceRepository.save(resource);
                eventPublisher.publishEvent(new ResourceUploadedEvent(resource.getId(), mime, null));
            } catch (IOException e) {
                throw new CiliException("Fehler beim Speichern des Bildes: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private byte[] convertToJpeg(InputStream in) throws IOException {
        BufferedImage src = ImageIO.read(in);
        if (src == null) throw new IOException("Bild konnte nicht dekodiert werden");
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(rgb, "jpg", out)) throw new IOException("Kein JPEG-Writer verfügbar");
        return out.toByteArray();
    }

    private void deleteResourceFile(Resource r) {
        try { storageService.delete(r.getStoredName()); } catch (IOException ignored) {}
    }

    private void validate(String authorName, String text) {
        if (authorName == null || authorName.isBlank())
            throw new CiliException("Name ist erforderlich", HttpStatus.BAD_REQUEST);
        if (authorName.trim().length() > 200)
            throw new CiliException("Name darf maximal 200 Zeichen lang sein", HttpStatus.BAD_REQUEST);
        if (text == null || text.trim().length() < 10)
            throw new CiliException("Text muss mindestens 10 Zeichen lang sein", HttpStatus.BAD_REQUEST);
    }

    private void validateCategory(boolean human, boolean animal) {
        if (!human && !animal) {
            throw new CiliException("Mindestens eine Kategorie (Mensch oder Tier) muss ausgewählt sein", HttpStatus.BAD_REQUEST);
        }
    }

    private Testimonial findOrThrow(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Testimonial", id));
    }

    private void checkOwnerOrAdmin(Testimonial t) {
        CiliUserDetails user = currentUser();
        if (!t.getUserId().equals(user.getUserId()) && user.getRole() != UserRole.ADMIN) {
            throw new CiliException("Zugriff verweigert", HttpStatus.FORBIDDEN);
        }
    }

    private List<String> parseTerms(String q) {
        return Arrays.stream(q.trim().split("\\s+"))
            .filter(t -> !t.isBlank())
            .toList();
    }

    private TestimonialDto toDto(Testimonial t) {
        List<Resource> resources = resourceRepository.findByTestimonialIdOrderByCreatedAtAsc(t.getId());
        Map<Long, Thumbnail> thumbsByResourceId = thumbnailsFor(resources);
        List<TestimonialAttachmentDto> attachments = resources.stream()
            .map(r -> toAttachmentDto(r, thumbsByResourceId.get(r.getId())))
            .toList();
        return new TestimonialDto(
            t.getId(), t.getAuthorName(), t.getTags(), t.getText(), t.isHuman(), t.isAnimal(),
            t.getUserId(), t.getCreatedAt(), t.getUpdatedAt(), attachments);
    }

    private Map<Long, Thumbnail> thumbnailsFor(List<Resource> resources) {
        return thumbnailRepository.findByResourceIdIn(resources.stream().map(Resource::getId).toList())
            .stream().collect(Collectors.toMap(Thumbnail::getResourceId, th -> th));
    }

    private TestimonialAttachmentDto toAttachmentDto(Resource r, Thumbnail thumbnail) {
        String status = thumbnail != null ? thumbnail.getStatus().name() : null;
        return new TestimonialAttachmentDto(r.getId(), r.getOriginalName(), r.getMimeType(), r.getSize(), r.getCreatedAt(),
            status, r.getStoredName());
    }

    private CiliUserDetails currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CiliUserDetails ud)) {
            throw new CiliException("Keine gültige Authentifizierung", HttpStatus.UNAUTHORIZED);
        }
        return ud;
    }

    @Transactional(readOnly = true)
    public List<TestimonialDto> listAll() {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PublicTestimonialDto> listAllPublic(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable)
                .stream().map(this::toPublicDto).toList();
    }

    @Transactional(readOnly = true)
    public List<TestimonialDto> getByIds(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return repository.findAllById(ids).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PublicTestimonialDto> getPublicByIds(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return repository.findAllById(ids).stream().map(this::toPublicDto).toList();
    }

    private PublicTestimonialDto toPublicDto(Testimonial t) {
        List<Resource> resources = resourceRepository.findByTestimonialIdOrderByCreatedAtAsc(t.getId());
        Map<Long, Thumbnail> thumbsByResourceId = thumbnailsFor(resources);
        List<TestimonialAttachmentDto> attachments = resources.stream()
            .map(r -> toAttachmentDto(r, thumbsByResourceId.get(r.getId())))
            .toList();
        return new PublicTestimonialDto(
            t.getId(), t.getAuthorName(), t.getTags(), t.getText(), t.isHuman(), t.isAnimal(),
            t.getCreatedAt(), t.getUpdatedAt(), attachments);
    }

    public byte[] getPublicThumbnailBytes(Long resourceId, String size) throws IOException {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Resource not found"));
        // folderId und testimonialId schließen sich gegenseitig aus (DB-Constraint),
        // daher reicht testimonialId != null als Sicherheitswächter.
        if (resource.getTestimonialId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Not a testimonial image");
        }
        return thumbnailService.getThumbnailBytesNoAcl(resourceId, size);
    }
}
