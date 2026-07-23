package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.Thumbnail;
import de.toengi.cili.model.enums.ThumbnailStatus;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.ThumbnailRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.images.Artwork;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThumbnailService {

    public static final String RESOURCE_NOT_STORED_LOCALLY = "Resource not stored locally";
    public static final String SCALE = "scale=";
    public static final String THUMBNAILS = "thumbnails/";
    public static final String THUMBNAIL_LOCAL_FILE_NOT_FOUND_FOR_RESOURCE_STORED_NAME = "[THUMBNAIL] Local file not found for resource {} (storedName={})";
    public static final String LARGE_JPG = "-large.jpg";
    public static final String SMALL_JPG = "-small.jpg";
    public static final String THUMBNAIL_RESOURCE_NOT_FOUND_IN_DB = "[THUMBNAIL] Resource {} not found in DB";
    private static final int SMALL_WIDTH = 320;
    private static final int LARGE_WIDTH = 1280;
    private final ThumbnailRepository thumbnailRepository;
    private final ResourceRepository resourceRepository;
    private final StorageService storageService;
    private final FileStorageConfig config;
    private final CommandRunner commandRunner;
    private final PlatformTransactionManager txManager;
    private final LibreOfficeConversionService libreOfficeConversionService;

    private static boolean isDocument(String mimeType) {
        return mimeType.equals("application/pdf")
                || mimeType.startsWith("application/vnd.openxmlformats")
                || mimeType.startsWith("application/vnd.ms-")
                || mimeType.equals("application/msword")
                || mimeType.startsWith("application/vnd.oasis.opendocument");
    }

    private static String fileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "bin" : filename.substring(dot + 1).toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Called from ResourceUploadedListener (already on thumbnailExecutor thread).
     */
    public void processUpload(Long resourceId, String mimeType) {
        log.info("[THUMBNAIL] processUpload called: resourceId={}, mimeType={}, thread={}",
                resourceId, mimeType, Thread.currentThread().getName());
        if (mimeType.startsWith("video/")) {
            log.debug("[THUMBNAIL] Routing to processVideoThumbnail for resource {}", resourceId);
            processVideoThumbnail(resourceId);
        } else if (mimeType.startsWith("image/")) {
            log.debug("[THUMBNAIL] Routing to processImageThumbnail for resource {}", resourceId);
            processImageThumbnail(resourceId);
        } else if (isDocument(mimeType)) {
            log.debug("[THUMBNAIL] Routing to processDocumentThumbnail for resource {}", resourceId);
            processDocumentThumbnail(resourceId);
        } else if (mimeType.startsWith("audio/")) {
            log.debug("[THUMBNAIL] Routing to processAudioThumbnail for resource {}", resourceId);
            processAudioThumbnail(resourceId);
        } else {
            log.info("[THUMBNAIL] No thumbnail handler for mimeType '{}' (resource {})", mimeType, resourceId);
        }
    }

    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public byte[] getThumbnailBytes(Long resourceId, String size) throws IOException {
        return getThumbnailBytesNoAcl(resourceId, size);
    }

    public byte[] getThumbnailBytesNoAcl(Long resourceId, String size) throws IOException {
        Optional<Thumbnail> thumbOpt = thumbnailRepository.findByResourceId(resourceId);
        if (thumbOpt.isPresent() && thumbOpt.get().getStatus() == ThumbnailStatus.DONE) {
            Thumbnail thumbnail = thumbOpt.get();
            String rel;
            if (size.equals("large")) {
                rel = thumbnail.getLargePath();
            } else {
                rel = thumbnail.getSmallPath();
            }
            if (rel != null) {
                return Files.readAllBytes(Paths.get(config.getBasePath()).resolve(rel));
            }
        }
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", resourceId));
        if (resource.getMimeType().startsWith("image/")) {
            Path original = storageService.resolveLocalPath(resource.getStoredName())
                    .orElseThrow(() -> new ResourceNotFoundException("Thumbnail", resourceId));
            return Files.readAllBytes(original);
        }
        throw new ResourceNotFoundException("Thumbnail", resourceId);
    }

    private void processVideoThumbnail(Long resourceId) {
        log.info("[THUMBNAIL] processVideoThumbnail started for resource {}", resourceId);
        Resource resource = resourceRepository.findById(resourceId).orElse(null);
        if (resource == null) {
            log.warn(THUMBNAIL_RESOURCE_NOT_FOUND_IN_DB, resourceId);
            return;
        }

        Thumbnail thumbnail = initThumbnail(resourceId);
        if (thumbnail == null) return;

        Optional<Path> pathOpt = storageService.resolveLocalPath(resource.getStoredName());
        if (pathOpt.isEmpty()) {
            log.warn(THUMBNAIL_LOCAL_FILE_NOT_FOUND_FOR_RESOURCE_STORED_NAME, resourceId, resource.getStoredName());
            markFailed(thumbnail, RESOURCE_NOT_STORED_LOCALLY);
            return;
        }

        try {
            Path input = pathOpt.get();
            log.debug("[THUMBNAIL] Video file path: {}", input);
            double duration = getVideoDuration(input);
            double seekSecs = Math.min(4.0, duration * 0.5);
            log.debug("[THUMBNAIL] Video duration={}s, seek={}s", String.format("%.1f", duration), String.format("%.1f", seekSecs));

            String uuid = UUID.randomUUID().toString();
            Path dir = thumbDir();
            Files.createDirectories(dir);

            String small = uuid + SMALL_JPG;
            String large = uuid + LARGE_JPG;

            boolean ok = runFrame(input, dir.resolve(small), SCALE + SMALL_WIDTH + ":-1", seekSecs)
                    && runFrame(input, dir.resolve(large), SCALE + LARGE_WIDTH + ":-1", seekSecs);

            if (ok) {
                log.info("[THUMBNAIL] Video thumbnail DONE for resource {}", resourceId);
                markDone(thumbnail, THUMBNAILS + small, THUMBNAILS + large);
            } else {
                log.warn("Video thumbnail generation failed for resource {} (duration={}s, seek={}s)",
                        resourceId, String.format("%.1f", duration), String.format("%.1f", seekSecs));
                markFailed(thumbnail, "FFmpeg returned non-zero for one or more sizes");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            markFailed(thumbnail, "Interrupted");
        } catch (IOException e) {
            log.error("Thumbnail generation failed for resource {}: {}", resourceId, e.getMessage());
            markFailed(thumbnail, e.getMessage());
        }
    }

    private void processImageThumbnail(Long resourceId) {
        log.info("[THUMBNAIL] processImageThumbnail started for resource {}", resourceId);
        Resource resource = resourceRepository.findById(resourceId).orElse(null);
        if (resource == null) {
            log.warn(THUMBNAIL_RESOURCE_NOT_FOUND_IN_DB, resourceId);
            return;
        }

        Thumbnail thumbnail = initThumbnail(resourceId);
        if (thumbnail == null) return;

        Optional<Path> pathOpt = storageService.resolveLocalPath(resource.getStoredName());
        if (pathOpt.isEmpty()) {
            log.warn(THUMBNAIL_LOCAL_FILE_NOT_FOUND_FOR_RESOURCE_STORED_NAME, resourceId, resource.getStoredName());
            markFailed(thumbnail, RESOURCE_NOT_STORED_LOCALLY);
            return;
        }

        try {
            Path input = pathOpt.get();
            String uuid = UUID.randomUUID().toString();
            Path dir = thumbDir();
            Files.createDirectories(dir);

            String small = uuid + SMALL_JPG;
            String large = uuid + LARGE_JPG;

            // FFmpeg wendet EXIF-Orientierung (Hochkant-/Querformat) automatisch an.
            // ImageIO ignoriert EXIF und würde Hochkantfotos immer als Querformat speichern.
            boolean ok = runImageFrame(input, dir.resolve(small), SCALE + SMALL_WIDTH + ":-1")
                    && runImageFrame(input, dir.resolve(large), SCALE + LARGE_WIDTH + ":-1");

            if (ok) {
                markDone(thumbnail, THUMBNAILS + small, THUMBNAILS + large);
            } else {
                // Fallback: ImageIO (ohne EXIF-Korrektur — besser als kein Thumbnail)
                log.warn("FFmpeg image thumbnail failed for resource {}, falling back to ImageIO", resourceId);
                BufferedImage original = ImageIO.read(input.toFile());
                if (original == null) {
                    markFailed(thumbnail, "ImageIO could not decode image");
                    return;
                }
                writeAllSizes(original, thumbnail);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            markFailed(thumbnail, "Interrupted");
        } catch (IOException e) {
            log.error("Image thumbnail generation failed for resource {}: {}", resourceId, e.getMessage());
            markFailed(thumbnail, e.getMessage());
        }
    }

    /**
     * Generiert ein Bild-Thumbnail via FFmpeg.
     * FFmpeg wendet EXIF-Orientierungsmetadaten beim Dekodieren automatisch an —
     * kein manuelles Lesen/Rotieren des EXIF-Tags notwendig.
     *
     * @param scaleFilter z.B. "scale=320:-1" oder null für Originalgröße
     */
    private boolean runImageFrame(Path input, Path output, String scaleFilter)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of(
                config.getFfmpegPath(), "-y",
                "-i", input.toString()));
        if (scaleFilter != null) {
            cmd.add("-vf");
            cmd.add(scaleFilter);
        }
        cmd.add(output.toString());
        log.debug("[THUMBNAIL] FFmpeg image command: {}", cmd);
        int rc = commandRunner.run(cmd);
        return rc == 0 && Files.exists(output) && Files.size(output) > 0;
    }

    private void processDocumentThumbnail(Long resourceId) {
        log.info("[THUMBNAIL] processDocumentThumbnail started for resource {}", resourceId);
        Resource resource = resourceRepository.findById(resourceId).orElse(null);
        if (resource == null) {
            log.warn(THUMBNAIL_RESOURCE_NOT_FOUND_IN_DB, resourceId);
            return;
        }

        Thumbnail thumbnail = initThumbnail(resourceId);
        if (thumbnail == null) return;

        Optional<Path> pathOpt = storageService.resolveLocalPath(resource.getStoredName());
        if (pathOpt.isEmpty()) {
            log.warn(THUMBNAIL_LOCAL_FILE_NOT_FOUND_FOR_RESOURCE_STORED_NAME, resourceId, resource.getStoredName());
            markFailed(thumbnail, RESOURCE_NOT_STORED_LOCALLY);
            return;
        }

        try {
            Path pdfPath = resolvePdf(resource, pathOpt.get());
            try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
                BufferedImage page = new PDFRenderer(doc).renderImageWithDPI(0, 150);
                writeAllSizes(page, thumbnail);
            }
        } catch (IOException e) {
            log.error("Document thumbnail generation failed for resource {}: {}", resourceId, e.getMessage());
            markFailed(thumbnail, e.getMessage());
        }
    }

    private void processAudioThumbnail(Long resourceId) {
        log.info("[THUMBNAIL] processAudioThumbnail started for resource {}", resourceId);
        Resource resource = resourceRepository.findById(resourceId).orElse(null);
        if (resource == null) {
            log.warn(THUMBNAIL_RESOURCE_NOT_FOUND_IN_DB, resourceId);
            return;
        }

        Thumbnail thumbnail = initThumbnail(resourceId);
        if (thumbnail == null) return;

        Optional<Path> pathOpt = storageService.resolveLocalPath(resource.getStoredName());
        if (pathOpt.isEmpty()) {
            log.warn(THUMBNAIL_LOCAL_FILE_NOT_FOUND_FOR_RESOURCE_STORED_NAME, resourceId, resource.getStoredName());
            markFailed(thumbnail, RESOURCE_NOT_STORED_LOCALLY);
            return;
        }

        String ext = fileExtension(resource.getOriginalName());
        Path tmpFile = null;
        try {
            // JAudioTagger detects format by file extension — copy to temp file with proper extension
            tmpFile = Files.createTempFile("cili-audio-", "." + ext);
            Files.copy(pathOpt.get(), tmpFile, StandardCopyOption.REPLACE_EXISTING);

            AudioFile audioFile = AudioFileIO.read(tmpFile.toFile());
            Tag tag = audioFile.getTag();
            if (tag == null) {
                markFailed(thumbnail, "No ID3 tag");
                return;
            }

            Artwork artwork = tag.getFirstArtwork();
            if (artwork == null) {
                markFailed(thumbnail, "No artwork");
                return;
            }

            byte[] imageData = artwork.getBinaryData();
            if (imageData == null || imageData.length == 0) {
                markFailed(thumbnail, "Empty artwork data");
                return;
            }

            BufferedImage cover = ImageIO.read(new ByteArrayInputStream(imageData));
            if (cover == null) {
                markFailed(thumbnail, "Could not decode artwork");
                return;
            }

            writeAllSizes(cover, thumbnail);
        } catch (IOException | CannotReadException | TagException | ReadOnlyFileException |
                 InvalidAudioFrameException e) {
            log.warn("Audio thumbnail failed for resource {}: {}", resourceId, e.getMessage());
            markFailed(thumbnail, e.getMessage());
        } finally {
            if (tmpFile != null) {
                try {
                    Files.deleteIfExists(tmpFile);
                } catch (IOException ex) {
                    log.warn(ex.getMessage());
                }
            }
        }
    }

    /**
     * Creates or resets the Thumbnail record to PROCESSING within its own transaction.
     */
    private Thumbnail initThumbnail(Long resourceId) {
        log.debug("[THUMBNAIL] initThumbnail: setting status=PROCESSING for resource {}", resourceId);
        return new TransactionTemplate(txManager).execute(status -> {
            Thumbnail t = thumbnailRepository.findByResourceId(resourceId)
                    .orElse(Thumbnail.builder().resourceId(resourceId).build());
            t.setStatus(ThumbnailStatus.PROCESSING);
            Thumbnail saved = thumbnailRepository.save(t);
            log.debug("[THUMBNAIL] Thumbnail record id={} saved with status=PROCESSING", saved.getId());
            return saved;
        });
    }

    /**
     * Scales to small and large and writes them, then marks the thumbnail DONE.
     */
    private void writeAllSizes(BufferedImage img, Thumbnail thumbnail) throws IOException {
        String uuid = UUID.randomUUID().toString();
        Path dir = thumbDir();
        Files.createDirectories(dir);

        String small = uuid + SMALL_JPG;
        String large = uuid + LARGE_JPG;

        scaleAndWrite(img, dir.resolve(small), SMALL_WIDTH);
        scaleAndWrite(img, dir.resolve(large), LARGE_WIDTH);
        markDone(thumbnail,
                THUMBNAILS + small,
                THUMBNAILS + large);
    }

    private Path thumbDir() {
        return Paths.get(config.getBasePath(), "thumbnails");
    }

    /**
     * Returns a PDF file for the resource — uses cached preview if available, runs LibreOffice otherwise.
     */
    private Path resolvePdf(Resource resource, Path inputPath) throws IOException {
        if ("application/pdf".equals(resource.getMimeType())) return inputPath;

        Path previewDir = Paths.get(config.getBasePath(), "previews");
        Path previewPath = previewDir.resolve(resource.getStoredName() + ".pdf");
        if (Files.exists(previewPath)) return previewPath;

        String ext = fileExtension(resource.getOriginalName());
        Path tmpInput = Files.createTempFile("cili-thumb-", "." + ext);
        try {
            Files.copy(inputPath, tmpInput, StandardCopyOption.REPLACE_EXISTING);
            Files.createDirectories(previewDir);

            int rc = libreOfficeConversionService.convert("pdf", previewDir, tmpInput);
            String baseName = tmpInput.getFileName().toString().replaceAll("\\.[^.]+$", "");
            Path libOut = previewDir.resolve(baseName + ".pdf");

            if (rc != 0 || !Files.exists(libOut)) {
                throw new IOException("LibreOffice conversion failed (exit " + rc + ")");
            }
            Files.move(libOut, previewPath, StandardCopyOption.REPLACE_EXISTING);
            return previewPath;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("LibreOffice conversion interrupted", ie);
        } finally {
            Files.deleteIfExists(tmpInput);
        }
    }

    private void scaleAndWrite(BufferedImage src, Path output, int targetWidth) throws IOException {
        int origW = src.getWidth();
        int origH = src.getHeight();
        int w = Math.min(origW, targetWidth);
        int h = origW > 0 ? (int) Math.round((double) origH / origW * w) : origH;

        // Always write TYPE_INT_RGB — JPEG encoder does not support alpha channels
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        if (!ImageIO.write(out, "jpg", output.toFile())) {
            throw new IOException("No JPEG writer available for image type " + src.getType());
        }
    }

    private void updateStatus(Thumbnail thumbnail, ThumbnailStatus status,
                              String small, String large, String error) {
        log.info("[THUMBNAIL] updateStatus: thumbnailId={}, status={}, error={}",
                thumbnail.getId(), status, error);
        new TransactionTemplate(txManager).execute(tx -> {
            thumbnail.setStatus(status);
            thumbnail.setSmallPath(small);
            thumbnail.setLargePath(large);
            thumbnail.setErrorMessage(error);
            thumbnailRepository.save(thumbnail);
            return null;
        });
    }

    private boolean runFrame(Path input, Path output, String scaleFilter, double seekSecs)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of(
                config.getFfmpegPath(), "-y",
                "-ss", String.format(java.util.Locale.ROOT, "%.3f", seekSecs),
                "-i", input.toString(),
                "-vframes", "1"));
        if (scaleFilter != null) {
            cmd.add("-vf");
            cmd.add(scaleFilter);
        }
        cmd.add(output.toString());
        log.debug("[THUMBNAIL] FFmpeg video frame command: {}", cmd);
        int rc = commandRunner.run(cmd);
        return rc == 0 && Files.exists(output) && Files.size(output) > 0;
    }

    private double getVideoDuration(Path input) {
        ProcessBuilder pb = new ProcessBuilder(
                config.getFfprobePath(),
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                input.toString());
        pb.redirectErrorStream(true);
        Process p = null;
        try {
            p = pb.start();
            String out;
            try (InputStream is = p.getInputStream()) {
                out = new String(is.readAllBytes()).trim();
            }
            p.waitFor();
            return Double.parseDouble(out);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("ffprobe interrupted for {}", input.getFileName());
            return 30.0;
        } catch (IOException e) {
            log.warn("ffprobe could not determine duration for {}: {}", input.getFileName(), e.getMessage());
            return 30.0;
        } finally {
            if (p != null) p.destroyForcibly();
        }
    }

    private void markFailed(Thumbnail thumbnail, String message) {
        updateStatus(
                thumbnail,
                ThumbnailStatus.FAILED,
                null,
                null,
                message);
    }

    private void markDone(
            Thumbnail thumbnail,
            String small,
            String large) {

        updateStatus(
                thumbnail,
                ThumbnailStatus.DONE,
                small,
                large,
                null);
    }
}
