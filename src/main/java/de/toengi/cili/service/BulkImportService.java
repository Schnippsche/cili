package de.toengi.cili.service;

import de.toengi.cili.dto.bulkimport.*;
import de.toengi.cili.dto.folder.CreateFolderRequest;
import de.toengi.cili.dto.folder.FolderDto;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.BulkImportItem;
import de.toengi.cili.model.entity.BulkImportJob;
import de.toengi.cili.model.entity.Folder;
import de.toengi.cili.model.enums.BulkImportItemStatus;
import de.toengi.cili.repository.BulkImportItemRepository;
import de.toengi.cili.repository.BulkImportJobRepository;
import de.toengi.cili.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkImportService {

    private static final Set<String> ALLOWED_MIME_PREFIXES = Set.of("video/", "audio/", "image/");

    // Subtitle-/Text-Formate, die kein PDF/Office-Dokument sind (dafür ist
    // TextExtractionService.supports() die Quelle der Wahrheit, s.u.).
    private static final Set<String> ALLOWED_TEXT_MIME_TYPES = Set.of("text/vtt", "text/plain");

    // Browser melden für viele Video-/Audio-Container einen leeren oder generischen
    // file.type (z.B. "application/octet-stream") — UploadService.normalizeMimeType()
    // kennt nur Text-Formate (.srt/.vtt/.csv/.xml), daher hier ein zusätzlicher
    // Endungs-Fallback NUR für die Klassifikationsentscheidung (überschreibt nicht
    // das gespeicherte BulkImportItem.mimeType, das bleibt der Rohwert vom Client).
    private static final Map<String, String> EXTENSION_MIME_FALLBACK = Map.ofEntries(
            Map.entry("mp4", "video/mp4"), Map.entry("mkv", "video/x-matroska"),
            Map.entry("avi", "video/x-msvideo"), Map.entry("mov", "video/quicktime"),
            Map.entry("webm", "video/webm"), Map.entry("flv", "video/x-flv"),
            Map.entry("wmv", "video/x-ms-wmv"), Map.entry("ts", "video/mp2t"),
            Map.entry("ogv", "video/ogg"), Map.entry("m4v", "video/x-m4v"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"),
            Map.entry("flac", "audio/flac"), Map.entry("ogg", "audio/ogg"),
            Map.entry("m4a", "audio/mp4")
    );

    private final BulkImportJobRepository bulkImportJobRepository;
    private final BulkImportItemRepository bulkImportItemRepository;
    private final FolderRepository folderRepository;
    private final FolderService folderService;
    private final TextExtractionService textExtractionService;

    @Transactional
    public CreateBulkImportResponse createImport(CreateBulkImportRequest request, Long adminUserId) {
        Map<String, Long> dirToFolderId = resolveFolderTree(request.targetFolderId(), request.entries(), adminUserId);

        String jobId = UUID.randomUUID().toString();
        List<BulkImportItem> items = new ArrayList<>();
        int skipped = 0;
        for (BulkImportEntry entry : request.entries()) {
            String fileName = fileNameOf(entry.relativePath());
            String normalizedMime = UploadService.normalizeMimeType(fileName, entry.mimeType());
            if (!isSupportedMimeType(normalizedMime)) {
                String fallback = EXTENSION_MIME_FALLBACK.get(extensionOf(fileName));
                if (fallback != null) normalizedMime = fallback;
            }
            BulkImportItem item = BulkImportItem.builder()
                    .bulkImportJobId(jobId)
                    .relativePath(entry.relativePath())
                    .fileSize(entry.fileSize())
                    .fileLastModified(entry.fileLastModified())
                    .mimeType(entry.mimeType())
                    .build();
            if (isSupportedMimeType(normalizedMime)) {
                item.setStatus(BulkImportItemStatus.PENDING);
                item.setResolvedFolderId(dirToFolderId.get(dirOf(entry.relativePath())));
            } else {
                item.setStatus(BulkImportItemStatus.SKIPPED);
                item.setSkipReason("Nicht unterstützter Dateityp: "
                        + (entry.mimeType() != null ? entry.mimeType() : "unbekannt"));
                skipped++;
            }
            items.add(item);
        }

        BulkImportJob job = BulkImportJob.builder()
                .id(jobId)
                .adminUserId(adminUserId)
                .targetFolderId(request.targetFolderId())
                .rootName(request.rootName())
                .filesTotal(request.entries().size())
                .filesSkipped(skipped)
                .build();
        bulkImportJobRepository.save(job);
        List<BulkImportItem> saved = bulkImportItemRepository.saveAll(items);

        log.info("Bulk-Import erstellt: user={} job={} rootName='{}' targetFolderId={} filesTotal={} filesSkipped={}",
                adminUserId, jobId, request.rootName(), request.targetFolderId(), request.entries().size(), skipped);

        return new CreateBulkImportResponse(jobId, saved.stream().map(this::toItemDto).toList());
    }

    @Transactional(readOnly = true)
    public BulkImportJobDto getJob(String jobId, Long actorUserId) {
        BulkImportJob job = getOwnedJob(jobId, actorUserId);
        List<BulkImportItem> items = bulkImportItemRepository.findByBulkImportJobId(jobId);
        return toJobDto(job, items);
    }

    @Transactional
    public void failItem(String jobId, Long itemId, String errorMessage, Long actorUserId) {
        getOwnedJob(jobId, actorUserId);
        BulkImportItem item = bulkImportItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("BulkImportItem", itemId));
        if (!item.getBulkImportJobId().equals(jobId)) {
            // jobId ownership was checked above, but that only proves THAT job belongs to the
            // caller — it says nothing about whether itemId actually belongs to it. Without this
            // check, admin A could pass their own (owned) jobId together with an itemId lifted
            // from admin B's job and mutate/mark-failed an item they have no relationship to at
            // all. From the caller's perspective this item simply doesn't exist within the given
            // job, so treat it the same as an unknown item.
            throw new ResourceNotFoundException("BulkImportItem", itemId);
        }
        if (item.getStatus() == BulkImportItemStatus.DONE || item.getStatus() == BulkImportItemStatus.FAILED) {
            return; // bereits terminal – No-Op (Schutz gegen doppelten Fail-Call nach Netzwerk-Timeout)
        }
        item.setStatus(BulkImportItemStatus.FAILED);
        item.setErrorMessage(errorMessage);
        bulkImportItemRepository.save(item);
        bulkImportJobRepository.incrementFilesFailed(jobId);
    }

    /** Lädt den Job und stellt sicher, dass er dem anfragenden Admin gehört (analog UploadService.getJobForUser). */
    private BulkImportJob getOwnedJob(String jobId, Long actorUserId) {
        BulkImportJob job = bulkImportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("BulkImportJob", jobId));
        if (!job.getAdminUserId().equals(actorUserId)) {
            throw new AccessDeniedException("Not your bulk import job");
        }
        return job;
    }

    /** Legt fehlende Unterordner unterhalb targetFolderId an; gibt Map "Verzeichnispfad -> FolderId" zurück ("" = targetFolderId selbst). */
    private Map<String, Long> resolveFolderTree(Long targetFolderId, List<BulkImportEntry> entries, Long adminUserId) {
        LinkedHashSet<String> uniqueDirs = new LinkedHashSet<>();
        for (BulkImportEntry entry : entries) {
            // Alle Vorfahren-Verzeichnisse aufnehmen, nicht nur das unmittelbare
            // Elternverzeichnis der Datei — sonst fehlen Zwischenordner ohne eigene
            // Datei direkt darin (z.B. "Season1" bei "Season1/Episode1/video.mp4").
            String dir = dirOf(entry.relativePath());
            while (!dir.isEmpty()) {
                uniqueDirs.add(dir);
                dir = parentOf(dir);
            }
        }
        List<String> sortedDirs = new ArrayList<>(uniqueDirs);
        sortedDirs.sort(Comparator.comparingInt(BulkImportService::depthOf));

        Map<String, Long> dirToFolderId = new HashMap<>();
        dirToFolderId.put("", targetFolderId);
        for (String dir : sortedDirs) {
            if (dir.isEmpty()) continue;
            String parentDir = parentOf(dir);
            String segment = lastSegment(dir);
            Long parentFolderId = dirToFolderId.get(parentDir);
            if (parentFolderId == null) {
                // Sollte durch die Tiefen-Sortierung + vollständige Vorfahren-Expansion oben
                // nie eintreten; Absicherung gegen Regressionen dieser Fehlerklasse.
                throw new IllegalStateException(
                        "Elternordner für '" + dir + "' noch nicht aufgelöst — Reihenfolge-/Expansionsfehler in resolveFolderTree");
            }
            List<Folder> existing = folderRepository.findByParentIdAndName(parentFolderId, segment);
            Long folderId;
            if (!existing.isEmpty()) {
                folderId = existing.get(0).getId();
            } else {
                FolderDto created = folderService.createFolder(
                        new CreateFolderRequest(segment, parentFolderId, null), adminUserId);
                folderId = created.id();
            }
            dirToFolderId.put(dir, folderId);
        }
        return dirToFolderId;
    }

    private boolean isSupportedMimeType(String mimeType) {
        if (mimeType == null) return false;
        if (ALLOWED_MIME_PREFIXES.stream().anyMatch(mimeType::startsWith)) return true;
        if (ALLOWED_TEXT_MIME_TYPES.contains(mimeType)) return true;
        return textExtractionService.supports(mimeType);
    }

    private static String dirOf(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash < 0 ? "" : relativePath.substring(0, slash);
    }

    private static String fileNameOf(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash < 0 ? relativePath : relativePath.substring(slash + 1);
    }

    private static String parentOf(String dir) {
        int slash = dir.lastIndexOf('/');
        return slash < 0 ? "" : dir.substring(0, slash);
    }

    private static String lastSegment(String dir) {
        int slash = dir.lastIndexOf('/');
        return slash < 0 ? dir : dir.substring(slash + 1);
    }

    private static int depthOf(String dir) {
        return dir.isEmpty() ? 0 : (int) dir.chars().filter(c -> c == '/').count() + 1;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    private BulkImportItemDto toItemDto(BulkImportItem item) {
        return new BulkImportItemDto(item.getId(), item.getRelativePath(), item.getResolvedFolderId(),
                item.getStatus(), item.getSkipReason(), item.getErrorMessage(), item.getResourceId());
    }

    private BulkImportJobDto toJobDto(BulkImportJob job, List<BulkImportItem> items) {
        int resolved = job.getFilesDone() + job.getFilesSkipped() + job.getFilesFailed();
        String status;
        if (resolved < job.getFilesTotal()) {
            status = "RUNNING";
        } else if (job.getFilesFailed() > 0) {
            status = "COMPLETED_WITH_ERRORS";
        } else {
            status = "COMPLETED";
        }
        return new BulkImportJobDto(job.getId(), job.getRootName(), job.getTargetFolderId(), status,
                job.getFilesTotal(), job.getFilesDone(), job.getFilesSkipped(), job.getFilesFailed(),
                items.stream().map(this::toItemDto).toList());
    }
}
