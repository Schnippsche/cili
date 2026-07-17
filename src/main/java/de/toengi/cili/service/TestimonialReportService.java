package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.config.ReportConfig;
import de.toengi.cili.dto.report.ReportImageDto;
import de.toengi.cili.dto.report.ReportTestimonialDto;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.Testimonial;
import de.toengi.cili.model.entity.Thumbnail;
import de.toengi.cili.model.enums.ThumbnailStatus;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.TestimonialRepository;
import de.toengi.cili.repository.ThumbnailRepository;
import de.toengi.cili.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestimonialReportService {

    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    private static final int  MAX_IMG_WIDTH   = 600;
    private static final int  MAX_IMG_HEIGHT  = 450;

    private final TestimonialRepository testimonialRepo;
    private final ResourceRepository resourceRepo;
    private final ThumbnailRepository thumbnailRepo;
    private final StorageService storageService;
    private final TemplateEngine templateEngine;
    private final ReportConfig reportConfig;
    private final FileStorageConfig fileStorageConfig;

    public List<Testimonial> fetchAll(String q, int max) {
        if (q == null || q.isBlank()) {
            return testimonialRepo.findTopNByCreatedAtDesc(max);
        }
        List<String> terms = java.util.Arrays.stream(q.trim().split("\\s+"))
            .filter(t -> !t.isBlank())
            .toList();
        return testimonialRepo.searchLikeTop(terms, max);
    }

    /**
     * Lädt Testimonials anhand einer festen ID-Liste (z. B. aus einer Sammlung) und
     * stellt dabei die exakte Reihenfolge der übergebenen {@code ids} wieder her, da
     * {@link TestimonialRepository#findAllById} keine Reihenfolge garantiert.
     * IDs, zu denen kein Testimonial (mehr) existiert, werden stillschweigend übersprungen.
     * Die Ergebnisliste wird auf {@code max} Einträge begrenzt (Truncation nach dem Reordering).
     */
    public List<Testimonial> fetchByIds(List<Long> ids, int max) {
        Map<Long, Testimonial> byId = testimonialRepo.findAllById(ids).stream()
            .collect(java.util.stream.Collectors.toMap(Testimonial::getId, t -> t));
        List<Testimonial> ordered = ids.stream()
            .map(byId::get)
            .filter(Objects::nonNull)
            .toList();
        if (ordered.size() > max) {
            return new ArrayList<>(ordered.subList(0, max));
        }
        return ordered;
    }

    public Map<Long, List<ReportImageDto>> loadImagesAsBase64(List<Testimonial> testimonials) {
        Map<Long, List<ReportImageDto>> result = new LinkedHashMap<>();
        for (Testimonial t : testimonials) {
            List<ReportImageDto> images = resourceRepo.findByTestimonialIdOrderByCreatedAtAsc(t.getId())
                    .stream()
                    .map(this::toImageDto)
                    .flatMap(Optional::stream)
                    .toList();
            result.put(t.getId(), images);
        }
        return result;
    }

    private Optional<ReportImageDto> toImageDto(Resource r) {
        try {
            Path thumbPath = resolveThumbPath(r);
            if (thumbPath != null) {
                byte[] bytes = Files.readAllBytes(thumbPath);
                return Optional.of(new ReportImageDto("image/jpeg", Base64.getEncoder().encodeToString(bytes), r.getOriginalName()));
            }
            if (r.getSize() != null && r.getSize() > MAX_IMAGE_BYTES) {
                log.warn("Bild {} ({} bytes) > 2 MB und kein Thumbnail vorhanden, wird übersprungen", r.getStoredName(), r.getSize());
                return Optional.empty();
            }
            byte[] bytes = readAndScale(r);
            return Optional.of(new ReportImageDto(r.getMimeType(), Base64.getEncoder().encodeToString(bytes), r.getOriginalName()));
        } catch (IOException e) {
            log.warn("Bild {} konnte nicht gelesen werden, wird übersprungen: {}", r.getStoredName(), e.getMessage());
            return Optional.empty();
        }
    }

    private Path resolveThumbPath(Resource r) {
        String thumbRel = thumbnailRepo.findByResourceId(r.getId())
                .filter(th -> th.getStatus() == ThumbnailStatus.DONE && th.getSmallPath() != null)
                .map(Thumbnail::getSmallPath)
                .orElse(null);
        if (thumbRel == null) return null;
        Path base = Paths.get(fileStorageConfig.getBasePath()).normalize();
        Path p = base.resolve(thumbRel).normalize();
        if (!p.startsWith(base)) {
            log.warn("Thumbnail-Pfad '{}' liegt außerhalb des Speicherverzeichnisses, wird übersprungen", thumbRel);
            return null;
        }
        return Files.exists(p) ? p : null;
    }

    public String renderHtml(String q, List<Testimonial> testimonials, boolean truncated, int maxResults) {
        Map<Long, List<ReportImageDto>> images = loadImagesAsBase64(testimonials);
        List<ReportTestimonialDto> dtos = testimonials.stream()
            .map(t -> new ReportTestimonialDto(
                t.getId(), t.getAuthorName(), t.getTags(), t.getText(),
                t.getCreatedAt(), images.getOrDefault(t.getId(), List.of())))
            .toList();

        Context ctx = new Context(Locale.GERMAN);
        ctx.setVariable("query",       q == null ? "" : q.trim());
        ctx.setVariable("generatedAt", LocalDate.now());
        ctx.setVariable("totalCount",  dtos.size());
        ctx.setVariable("truncated",   truncated);
        ctx.setVariable("maxResults",  maxResults);
        ctx.setVariable("testimonials", dtos);
        return templateEngine.process("testimonial-report", ctx);
    }

    private byte[] readAndScale(Resource r) throws IOException {
        try (InputStream is = storageService.retrieve(r.getStoredName())) {
            return scaleImageIfNeeded(is.readAllBytes(), r.getMimeType());
        }
    }

    private static byte[] scaleImageIfNeeded(byte[] bytes, String mimeType) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
            if (src == null || (src.getWidth() <= MAX_IMG_WIDTH && src.getHeight() <= MAX_IMG_HEIGHT)) {
                return bytes;
            }
            double scale = Math.min((double) MAX_IMG_WIDTH / src.getWidth(),
                                    (double) MAX_IMG_HEIGHT / src.getHeight());
            int w = Math.max(1, (int) (src.getWidth()  * scale));
            int h = Math.max(1, (int) (src.getHeight() * scale));
            boolean hasPng = mimeType != null && mimeType.contains("png");
            BufferedImage dst = new BufferedImage(w, h,
                hasPng ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(dst, hasPng ? "png" : "jpeg", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("Bild konnte nicht skaliert werden, Original wird verwendet: {}", e.getMessage());
            return bytes;
        }
    }

}
