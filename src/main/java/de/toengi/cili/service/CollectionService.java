package de.toengi.cili.service;

import de.toengi.cili.dto.collection.CollectionDto;
import de.toengi.cili.dto.resource.ResourceDto;
import de.toengi.cili.dto.testimonial.TestimonialDto;
import de.toengi.cili.model.entity.Collection;
import de.toengi.cili.model.entity.CollectionItem;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.Testimonial;
import de.toengi.cili.model.enums.AclPermission;
import de.toengi.cili.model.enums.AclResourceType;
import de.toengi.cili.model.enums.UserRole;
import de.toengi.cili.repository.CollectionItemRepository;
import de.toengi.cili.repository.CollectionRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.TestimonialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionService {

    private final CollectionRepository collectionRepo;
    private final CollectionItemRepository itemRepo;
    private final ResourceRepository resourceRepo;
    private final AclService aclService;
    private final ResourceService resourceService;
    private final TestimonialRepository testimonialRepo;
    private final TestimonialService testimonialService;

    @Transactional(readOnly = true)
    public List<CollectionDto> listForUser(Long userId) {
        return collectionRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CollectionDto> listTemplates() {
        return collectionRepo.findByIsTemplateTrueOrderByNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public CollectionDto getOne(Long userId, UserRole role, Long collectionId) {
        Collection c = requireManageable(userId, role, collectionId);
        return toDto(c);
    }

    @Transactional
    public CollectionDto create(Long userId, UserRole role, String name, boolean isTemplate) {
        if (isTemplate && !aclService.hasCollectionsPermission(userId, AclPermission.MANAGE_TEMPLATES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Keine Berechtigung, Vorlagen zu erstellen");
        }
        if (collectionRepo.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Eine Sammlung mit diesem Namen existiert bereits");
        }
        Collection c = collectionRepo.save(Collection.builder().userId(userId).name(name).isTemplate(isTemplate).build());
        log.info("Sammlung angelegt: user={} collectionId={} name='{}' isTemplate={}", userId, c.getId(), name, isTemplate);
        return toDto(c);
    }

    @Transactional
    public CollectionDto createFromTemplate(Long userId, Long templateId, String name) {
        Collection template = collectionRepo.findById(templateId)
                .filter(Collection::isTemplate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
        if (collectionRepo.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Eine Sammlung mit diesem Namen existiert bereits");
        }
        Collection created = collectionRepo.save(Collection.builder().userId(userId).name(name).build());
        List<CollectionItem> templateItems = itemRepo.findByCollectionId(templateId);
        List<CollectionItem> copies = templateItems.stream()
                .map(item -> CollectionItem.builder()
                        .collectionId(created.getId())
                        .resourceId(item.getResourceId())
                        .testimonialId(item.getTestimonialId())
                        .build())
                .toList();
        if (!copies.isEmpty()) {
            itemRepo.saveAll(copies);
        }
        log.info("Sammlung aus Vorlage erstellt: user={} templateId={} newCollectionId={} kopierteEinträge={}",
                userId, templateId, created.getId(), copies.size());
        return toDto(created);
    }

    @Transactional
    public CollectionDto rename(Long userId, UserRole role, Long collectionId, String newName) {
        Collection c = requireManageable(userId, role, collectionId);
        if (collectionRepo.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, newName, collectionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Eine Sammlung mit diesem Namen existiert bereits");
        }
        c.setName(newName);
        c = collectionRepo.save(c);
        return toDto(c);
    }

    @Transactional
    public CollectionDto copy(Long userId, UserRole role, Long collectionId, String newName) {
        Collection source = requireManageable(userId, role, collectionId);
        if (collectionRepo.existsByUserIdAndNameIgnoreCase(userId, newName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Eine Sammlung mit diesem Namen existiert bereits");
        }
        Collection copied = collectionRepo.save(Collection.builder().userId(userId).name(newName).isTemplate(source.isTemplate()).build());
        List<CollectionItem> sourceItems = itemRepo.findByCollectionId(collectionId);
        List<CollectionItem> copies = sourceItems.stream()
                .map(item -> CollectionItem.builder()
                        .collectionId(copied.getId())
                        .resourceId(item.getResourceId())
                        .testimonialId(item.getTestimonialId())
                        .build())
                .toList();
        if (!copies.isEmpty()) {
            itemRepo.saveAll(copies);
        }
        log.info("Sammlung kopiert: user={} sourceId={} newId={} name='{}' kopierteEinträge={}",
                userId, source.getId(), copied.getId(), newName, copies.size());
        return toDto(copied);
    }

    @Transactional
    public void delete(Long userId, UserRole role, Long collectionId) {
        Collection c = requireManageable(userId, role, collectionId);
        collectionRepo.delete(c);
        log.info("Sammlung gelöscht: user={} collectionId={} name='{}'", userId, collectionId, c.getName());
    }

    @Transactional
    public void addItem(Long userId, UserRole role, Long collectionId, Long resourceId) {
        requireManageable(userId, role, collectionId);
        resourceRepo.findById(resourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        if (itemRepo.existsByCollectionIdAndResourceId(collectionId, resourceId)) {
            return; // Bereits in Sammlung — kein Fehler
        }
        itemRepo.save(CollectionItem.builder()
                .collectionId(collectionId)
                .resourceId(resourceId)
                .build());
        log.info("Ressource zu Sammlung hinzugefügt: user={} collectionId={} resourceId={}", userId, collectionId, resourceId);
    }

    @Transactional
    public void removeItem(Long userId, UserRole role, Long collectionId, Long resourceId) {
        requireManageable(userId, role, collectionId);
        itemRepo.findByCollectionIdAndResourceId(collectionId, resourceId).ifPresent(item -> {
            itemRepo.delete(item);
            log.info("Ressource aus Sammlung entfernt: user={} collectionId={} resourceId={}", userId, collectionId, resourceId);
        });
    }

    @Transactional
    public void addTestimonialItem(Long userId, UserRole role, Long collectionId, Long testimonialId) {
        requireManageable(userId, role, collectionId);
        if (!aclService.hasTestimonialsPermission(userId, AclPermission.READ)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Kein Zugriff auf Erfahrungsberichte");
        }
        testimonialRepo.findById(testimonialId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Testimonial not found"));
        if (itemRepo.existsByCollectionIdAndTestimonialId(collectionId, testimonialId)) {
            return; // Bereits in Sammlung — kein Fehler
        }
        itemRepo.save(CollectionItem.builder()
                .collectionId(collectionId)
                .testimonialId(testimonialId)
                .build());
        log.info("Erfahrungsbericht zu Sammlung hinzugefügt: user={} collectionId={} testimonialId={}", userId, collectionId, testimonialId);
    }

    @Transactional
    public void removeTestimonialItem(Long userId, UserRole role, Long collectionId, Long testimonialId) {
        requireManageable(userId, role, collectionId);
        itemRepo.findByCollectionIdAndTestimonialId(collectionId, testimonialId).ifPresent(item -> {
            itemRepo.delete(item);
            log.info("Erfahrungsbericht aus Sammlung entfernt: user={} collectionId={} testimonialId={}", userId, collectionId, testimonialId);
        });
    }

    @Transactional(readOnly = true)
    public List<TestimonialDto> listTestimonialItems(Long userId, UserRole role, Long collectionId) {
        requireManageable(userId, role, collectionId);
        if (!aclService.hasTestimonialsPermission(userId, AclPermission.READ)) {
            return List.of();
        }
        List<CollectionItem> items = itemRepo.findByCollectionIdAndTestimonialIdIsNotNullOrderByAddedAtDesc(collectionId);
        List<Long> testimonialIds = items.stream().map(CollectionItem::getTestimonialId).toList();
        Map<Long, TestimonialDto> dtoMap = testimonialService.getByIds(testimonialIds).stream()
                .collect(java.util.stream.Collectors.toMap(TestimonialDto::id, dto -> dto));
        return items.stream()
                .map(item -> dtoMap.get(item.getTestimonialId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Liefert die geordnete Liste der Testimonial-IDs einer Sammlung (gleiche Reihenfolge
     * und gleiche Quelle wie {@link #listTestimonialItems}), nach Durchsetzung der
     * Besitz-/Zugriffsprüfung — zur Verwendung durch die Report-Generierung im Controller.
     */
    @Transactional(readOnly = true)
    public List<Long> requireTestimonialIdsForReport(Long userId, UserRole role, Long collectionId) {
        requireManageable(userId, role, collectionId);
        if (!aclService.hasTestimonialsPermission(userId, AclPermission.READ)) {
            return List.of();
        }
        return itemRepo.findByCollectionIdAndTestimonialIdIsNotNullOrderByAddedAtDesc(collectionId).stream()
                .map(CollectionItem::getTestimonialId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ResourceDto> listItems(Long userId, UserRole role, Long collectionId) {
        requireManageable(userId, role, collectionId);
        List<CollectionItem> items = itemRepo.findByCollectionIdAndResourceIdIsNotNullOrderByAddedAtDesc(collectionId);
        List<Long> resourceIds = items.stream().map(CollectionItem::getResourceId).toList();
        Map<Long, Resource> resourceMap = resourceRepo.findAllById(resourceIds).stream()
                .collect(java.util.stream.Collectors.toMap(Resource::getId, r -> r));
        List<Resource> filtered = items.stream()
                .map(item -> resourceMap.get(item.getResourceId()))
                .filter(Objects::nonNull)
                .filter(r -> {
                    if (r.getFolderId() == null) return true;
                    return aclService.hasPermission(userId, r.getFolderId(), AclResourceType.FOLDER, AclPermission.READ);
                })
                .toList();
        return resourceService.toDtoList(filtered);
    }

    private Collection requireManageable(Long userId, UserRole role, Long collectionId) {
        Optional<Collection> owned = collectionRepo.findByIdAndUserId(collectionId, userId);
        if (owned.isPresent()) return owned.get();
        if (role == UserRole.ADMIN) {
            Collection c = collectionRepo.findById(collectionId).orElse(null);
            if (c != null && c.isTemplate()) return c;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Collection not found or access denied");
    }

    private CollectionDto toDto(Collection c) {
        long itemCount = itemRepo.countByCollectionIdAndResourceIdIsNotNull(c.getId());
        long testimonialCount = itemRepo.countByCollectionIdAndTestimonialIdIsNotNull(c.getId());
        return new CollectionDto(c.getId(), c.getName(), itemCount, testimonialCount, c.isTemplate(), c.getCreatedAt());
    }
}
