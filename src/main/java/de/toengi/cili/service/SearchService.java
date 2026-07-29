package de.toengi.cili.service;

import de.toengi.cili.dto.search.*;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.Thumbnail;
import de.toengi.cili.repository.TestimonialRepository;
import de.toengi.cili.model.entity.ResourceMetadata;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.AclResourceType;
import de.toengi.cili.repository.ResourceMetadataRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import de.toengi.cili.repository.ThumbnailRepository;
import de.toengi.cili.security.CiliUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final ResourceRepository resourceRepository;
    private final ResourceMetadataRepository metadataRepository;
    private final SubtitleTrackRepository subtitleTrackRepository;
    private final ThumbnailRepository thumbnailRepository;
    private final AclService aclService;

    private final TestimonialRepository testimonialRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${cili.search.max-snippets-per-hit:3}")
    private int maxSnippetsPerHit;

    @Value("${cili.search.use-fulltext:true}")
    private boolean useFulltext = true; // true kept as field default so @InjectMocks unit tests work

    private static final Pattern TS_PATTERN = Pattern.compile(
            "(\\d{1,2}:\\d{2}:\\d{2}[.,]\\d{3})\\s*-->");

    private static final int TESTIMONIAL_PAGE_SIZE = 10;

    @PreAuthorize("isAuthenticated()")
    public SearchResponse search(String q, Long folderId, String mimeType, Pageable pageable) {
        return search(q, folderId, mimeType, pageable, 0);
    }

    @PreAuthorize("isAuthenticated()")
    public SearchResponse search(String q, Long folderId, String mimeType, Pageable pageable, int testimonialPage) {
        CiliUserDetails user = currentUser();
        if (q != null && !q.isBlank()) {
            log.info("Suche: user='{}' query='{}' folderId={}", user != null ? user.getUsername() : "?", q.trim(), folderId);
        }
        boolean isAdmin = isAdmin(user);
        if (!checkFolderAccess(user, isAdmin, folderId)) {
            return new SearchResponse(List.of(), 0L, pageable.getPageNumber(), pageable.getPageSize(),
                    List.of(), 0L, testimonialPage, TESTIMONIAL_PAGE_SIZE);
        }
        List<Long> folderFilter = getFolderFilter(user, isAdmin, folderId);
        List<String> terms = parseTerms(q);
        Page<Resource> page = fetchPage(terms, folderId, folderFilter, pageable);
        return buildResponse(page, terms, folderId, pageable, testimonialPage);
    }

    private boolean isAdmin(CiliUserDetails user) {
        return user != null && user.getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_ADMIN"));
    }

    private boolean checkFolderAccess(CiliUserDetails user, boolean isAdmin, Long folderId) {
        if (folderId == null || isAdmin || user == null) return true;
        return aclService.hasPermission(user.getUserId(), folderId, AclResourceType.FOLDER, AclPermission.READ);
    }

    private List<Long> getFolderFilter(CiliUserDetails user, boolean isAdmin, Long folderId) {
        if (folderId != null || isAdmin || user == null) return null;
        return aclService.getAccessibleFolderIds(user.getUserId()).orElse(null);
    }

    private List<String> parseTerms(String q) {
        if (q == null || q.isBlank()) return List.of();
        return Arrays.stream(q.trim().split("\\s+")).filter(t -> !t.isBlank()).toList();
    }

    private Page<Resource> fetchPage(List<String> terms, Long folderId, List<Long> folderFilter, Pageable pageable) {
        if (terms.isEmpty()) return fetchNoTermsPage(folderId, folderFilter, pageable);
        if (terms.size() == 1) return fetchSingleTermPage(terms.getFirst(), folderId, folderFilter, pageable);
        return findByMultipleTerms(terms, folderId, folderFilter, pageable);
    }

    private Page<Resource> fetchNoTermsPage(Long folderId, List<Long> folderFilter, Pageable pageable) {
        if (folderId != null) return resourceRepository.findByFolderId(folderId, pageable);
        if (folderFilter != null) return resourceRepository.findByFolderIdIn(folderFilter, pageable);
        return resourceRepository.findAllInFolders(pageable);
    }

    private Page<Resource> fetchSingleTermPage(String term, Long folderId, List<Long> folderFilter, Pageable pageable) {
        if (!useFulltext) {
            return executeNativeLikeSearch("%" + term.toLowerCase() + "%", folderId, folderFilter, pageable);
        }
        String boolQ = toBoolQ(List.of(term));
        if (folderId != null) return resourceRepository.searchByFolderAndNameOrMetadata(folderId, boolQ, pageable);
        if (folderFilter != null) return resourceRepository.searchByNameOrMetadataAndFolderIn(folderFilter, boolQ, pageable);
        return resourceRepository.searchByNameOrMetadata(boolQ, pageable);
    }

    private static String toBoolQ(List<String> terms) {
        return terms.stream()
            .map(t -> t.replaceAll("[+\\-~*<>()\"@]", ""))
            .filter(t -> !t.isBlank())
            .map(t -> "+" + t + "*")
            .collect(Collectors.joining(" "));
    }

    private SearchResponse buildResponse(Page<Resource> page, List<String> terms, Long folderId, Pageable pageable,
                                          int testimonialPage) {
        List<Long> ids = page.getContent().stream().map(Resource::getId).toList();
        Map<Long, ResourceMetadata> metaById = metadataRepository.findByResourceIdIn(ids)
                .stream().collect(Collectors.toMap(ResourceMetadata::getResourceId, m -> m));
        Map<Long, String> thumbStatusById = thumbnailRepository.findByResourceIdIn(ids)
                .stream().collect(Collectors.toMap(Thumbnail::getResourceId, t -> t.getStatus().name()));
        Map<Long, List<SnippetDto>> snippets = terms.isEmpty() || ids.isEmpty() ? Map.of()
                : extractSnippets(ids, terms.getFirst().toLowerCase());
        List<SearchHitDto> dtos = page.getContent().stream()
                .map(r -> toDto(r, metaById.get(r.getId()), snippets.getOrDefault(r.getId(), List.of()),
                        thumbStatusById.get(r.getId())))
                .toList();
        Page<TestimonialSearchHitDto> testimonials = fetchTestimonialHits(terms, folderId, testimonialPage);
        return new SearchResponse(dtos, page.getTotalElements(), pageable.getPageNumber(), pageable.getPageSize(),
                testimonials.getContent(), testimonials.getTotalElements(), testimonialPage, TESTIMONIAL_PAGE_SIZE);
    }

    private Page<TestimonialSearchHitDto> fetchTestimonialHits(List<String> terms, Long folderId, int testimonialPage) {
        if (terms.isEmpty() || folderId != null) return Page.empty();
        CiliUserDetails user = currentUser();
        if (user == null || !aclService.hasTestimonialsPermission(user.getUserId(), AclPermission.READ)) {
            return Page.empty();
        }
        Pageable tPageable = PageRequest.of(testimonialPage, TESTIMONIAL_PAGE_SIZE);
        return testimonialRepository.searchLike(terms, null, tPageable)
                .map(t -> new TestimonialSearchHitDto(
                    t.getId(), t.getAuthorName(), t.getTags(), t.getText(), t.getSource(), t.getCreatedAt()));
    }

    public FacetsResponse getFacets(String q) {
        String safe = (q == null) ? "" : q;
        List<FacetDto> mimeTypes = resourceRepository.countByMimeType(safe).stream()
                .map(row -> new FacetDto(
                        row[0] instanceof String s ? s : String.valueOf(row[0]),
                        row[1] instanceof Number n ? n.longValue() : 0L))
                .toList();
        List<FacetDto> languages = resourceRepository.countByLanguage(safe).stream()
                .map(row -> new FacetDto(
                        row[0] instanceof String s ? s : String.valueOf(row[0]),
                        row[1] instanceof Number n ? n.longValue() : 0L))
                .toList();
        return new FacetsResponse(mimeTypes, languages);
    }

    private static String normalizeUmlauts(String s) {
        return s.replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss")
                .replace("Ä", "A").replace("Ö", "O").replace("Ü", "U");
    }

    private Map<Long, List<SnippetDto>> extractSnippets(List<Long> resourceIds, String lowerQuery) {
        List<SubtitleTrack> tracks = subtitleTrackRepository.findWithTextContentByResourceIdIn(resourceIds);
        Map<Long, List<SnippetDto>> result = new HashMap<>();
        String normalizedQuery = normalizeUmlauts(lowerQuery);
        for (SubtitleTrack track : tracks) {
            if (result.containsKey(track.getResourceId())) continue;
            List<SnippetDto> snippets = extractMultipleSnippets(track.getTextContent(), normalizedQuery, track.getLanguageCode());
            if (!snippets.isEmpty()) result.put(track.getResourceId(), snippets);
        }
        return result;
    }

    private List<SnippetDto> extractMultipleSnippets(String textContent, String normalizedQuery, String language) {
        List<SnippetDto> results = new ArrayList<>();
        String lowerContent = normalizeUmlauts(textContent.toLowerCase());
        int searchFrom = 0;
        while (results.size() < maxSnippetsPerHit) {
            int idx = lowerContent.indexOf(normalizedQuery, searchFrom);
            if (idx < 0) break;
            String before = textContent.substring(0, idx);
            Matcher m = TS_PATTERN.matcher(before);
            String rawTs = null;
            while (m.find()) rawTs = m.group(1);
            int start = Math.max(0, idx - 80);
            int end = Math.min(textContent.length(), idx + normalizedQuery.length() + 80);
            String snippet = textContent.substring(start, end)
                    .replaceAll("(?m)^\\d{1,2}:\\d{2}:\\d{2}[.,]\\d{3}\\s*-->\\s*\\d{1,2}:\\d{2}:\\d{2}[.,]\\d{3}\\s*$", "")
                    .replaceAll("(?m)^\\d+\\s*$", "")
                    .replaceAll("(?m)^WEBVTT.*$", "")
                    .replaceAll("[\\n\\r]+", " ")
                    .replaceAll("\\s{2,}", " ")
                    .trim();
            if (!snippet.isBlank()) {
                results.add(new SnippetDto(
                        "…" + snippet + "…",
                        rawTs != null ? formatTimestamp(rawTs) : null,
                        rawTs != null ? toSeconds(rawTs) : null,
                        language));
            }
            searchFrom = idx + normalizedQuery.length() + 160;
        }
        return results;
    }

    private String formatTimestamp(String raw) {
        String[] parts = raw.replace(",", ".").split(":");
        int h = Integer.parseInt(parts[0]);
        int min = Integer.parseInt(parts[1]);
        int sec = (int) Double.parseDouble(parts[2]);
        return h > 0
                ? String.format("%d:%02d:%02d", h, min, sec)
                : String.format("%d:%02d", min, sec);
    }

    private int toSeconds(String raw) {
        String[] parts = raw.replace(",", ".").split(":");
        int h = Integer.parseInt(parts[0]);
        int min = Integer.parseInt(parts[1]);
        int sec = (int) Double.parseDouble(parts[2]);
        return h * 3600 + min * 60 + sec;
    }

    @SuppressWarnings("unchecked")
    private Page<Resource> executeNativeLikeSearch(String like, Long folderId, List<Long> folderFilter, Pageable pageable) {
        String base = "FROM resources r LEFT JOIN resource_metadata rm ON rm.resource_id = r.id WHERE r.folder_id IS NOT NULL ";
        StringBuilder where = new StringBuilder();
        if (folderId != null) where.append("AND r.folder_id = :folderId ");
        if (folderFilter != null) where.append("AND r.folder_id IN (:folderFilter) ");
        where.append("AND (LOWER(r.original_name) LIKE :like " +
            "OR LOWER(rm.title) LIKE :like OR LOWER(rm.description) LIKE :like " +
            "OR LOWER(rm.tags) LIKE :like OR LOWER(rm.categories) LIKE :like " +
            "OR LOWER(rm.text_content) LIKE :like " +
            "OR EXISTS (SELECT 1 FROM subtitle_tracks st WHERE st.resource_id = r.id " +
            "           AND LOWER(st.text_content) LIKE :like)) ");
        var countQ = entityManager.createNativeQuery("SELECT COUNT(DISTINCT r.id) " + base + where);
        var dataQ  = entityManager.createNativeQuery(
            "SELECT DISTINCT r.id, r.folder_id, r.testimonial_id, r.original_name, r.stored_name, r.mime_type, " +
            "r.size, r.checksum, r.uploader_id, r.storage_type, r.file_date, r.sort_order, r.created_at, r.updated_at " +
            base + where + "ORDER BY r.created_at DESC", Resource.class);
        if (folderId != null)     { countQ.setParameter("folderId", folderId);     dataQ.setParameter("folderId", folderId); }
        if (folderFilter != null) { countQ.setParameter("folderFilter", folderFilter); dataQ.setParameter("folderFilter", folderFilter); }
        countQ.setParameter("like", like); dataQ.setParameter("like", like);
        long total = ((Number) countQ.getSingleResult()).longValue();
        dataQ.setFirstResult((int) pageable.getOffset()); dataQ.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(dataQ.getResultList(), pageable, total);
    }

    @SuppressWarnings("unchecked")
    private Page<Resource> findByMultipleTerms(List<String> terms, Long folderId,
                                                List<Long> folderFilter, Pageable pageable) {
        if (!useFulltext) {
            String like = "%" + terms.stream().map(String::toLowerCase).collect(Collectors.joining("%")) + "%";
            return executeNativeLikeSearch(like, folderId, folderFilter, pageable);
        }
        String boolQ = toBoolQ(terms);
        String ftWhere =
            "AND (MATCH(r.original_name) AGAINST (:boolQ IN BOOLEAN MODE) " +
            "OR MATCH(rm.title, rm.description, rm.tags, rm.categories) AGAINST (:boolQ IN BOOLEAN MODE) " +
            "OR MATCH(rm.text_content) AGAINST (:boolQ IN BOOLEAN MODE) " +
            "OR EXISTS (SELECT 1 FROM subtitle_tracks st " +
            "           WHERE st.resource_id = r.id " +
            "           AND MATCH(st.text_content) AGAINST (:boolQ IN BOOLEAN MODE))) ";
        String base =
            "FROM resources r " +
            "LEFT JOIN resource_metadata rm ON rm.resource_id = r.id " +
            "WHERE r.folder_id IS NOT NULL ";
        StringBuilder where = new StringBuilder();
        if (folderId != null) where.append("AND r.folder_id = :folderId ");
        if (folderFilter != null) where.append("AND r.folder_id IN (:folderFilter) ");
        where.append(ftWhere);
        jakarta.persistence.Query countQ = entityManager.createNativeQuery(
                "SELECT COUNT(DISTINCT r.id) " + base + where);
        jakarta.persistence.Query dataQ = entityManager.createNativeQuery(
                "SELECT DISTINCT r.id, r.folder_id, r.testimonial_id, r.original_name, r.stored_name, r.mime_type, " +
                "r.size, r.checksum, r.uploader_id, r.storage_type, r.file_date, r.sort_order, r.created_at, r.updated_at " +
                base + where + "ORDER BY r.created_at DESC", Resource.class);
        if (folderId != null) { countQ.setParameter("folderId", folderId); dataQ.setParameter("folderId", folderId); }
        if (folderFilter != null) {
            countQ.setParameter("folderFilter", folderFilter);
            dataQ.setParameter("folderFilter", folderFilter);
        }
        countQ.setParameter("boolQ", boolQ);
        dataQ.setParameter("boolQ", boolQ);
        long total = ((Number) countQ.getSingleResult()).longValue();
        dataQ.setFirstResult((int) pageable.getOffset());
        dataQ.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(dataQ.getResultList(), pageable, total);
    }

    private SearchHitDto toDto(Resource resource, ResourceMetadata meta, List<SnippetDto> snippets, String thumbnailStatus) {
        return new SearchHitDto(
                resource.getId(),
                resource.getOriginalName(),
                meta != null ? meta.getTitle() : null,
                resource.getMimeType(),
                resource.getSize(),
                resource.getFolderId(),
                null,
                resource.getCreatedAt(),
                1.0f,
                snippets,
                thumbnailStatus,
                resource.getStoredName()
        );
    }

    private CiliUserDetails currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CiliUserDetails ud)) return null;
        return ud;
    }
}
