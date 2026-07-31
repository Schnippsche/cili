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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock private CollectionRepository collectionRepo;
    @Mock private CollectionItemRepository itemRepo;
    @Mock private ResourceRepository resourceRepo;
    @Mock private AclService aclService;
    @Mock private ResourceService resourceService;
    @Mock private TestimonialRepository testimonialRepo;
    @Mock private TestimonialService testimonialService;

    @InjectMocks private CollectionService service;

    private final Long userId = 1L;
    private final Long otherUserId = 2L;

    @Test
    void listForUser_returnsOnlyOwnCollections() {
        Collection c = Collection.builder().id(10L).userId(userId).name("Test").createdAt(LocalDateTime.now()).build();
        when(collectionRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(c));
        when(itemRepo.countByCollectionIdAndResourceIdIsNotNull(10L)).thenReturn(3L);

        List<CollectionDto> result = service.listForUser(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Test");
        assertThat(result.getFirst().itemCount()).isEqualTo(3L);
    }

    @Test
    void listForUser_itemCountExcludesTestimonials() {
        Collection c = Collection.builder().id(10L).userId(userId).name("Test").createdAt(LocalDateTime.now()).build();
        when(collectionRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(c));
        // Sammlung enthält 2 Ressourcen und 3 Testimonials — Repository liefert nur die Ressourcen-Anzahl
        when(itemRepo.countByCollectionIdAndResourceIdIsNotNull(10L)).thenReturn(2L);

        List<CollectionDto> result = service.listForUser(userId);

        assertThat(result.getFirst().itemCount()).isEqualTo(2L);
    }

    @Test
    void create_savesAndReturnsDto() {
        Collection saved = Collection.builder().id(5L).userId(userId).name("Neu").createdAt(LocalDateTime.now()).build();
        when(collectionRepo.save(any())).thenReturn(saved);
        when(itemRepo.countByCollectionIdAndResourceIdIsNotNull(5L)).thenReturn(0L);

        CollectionDto dto = service.create(userId, UserRole.USER, "Neu", false);

        assertThat(dto.id()).isEqualTo(5L);
        assertThat(dto.name()).isEqualTo("Neu");
    }

    @Test
    void create_withTemplatePermissionAndFlag_savesAsTemplate() {
        Collection saved = Collection.builder().id(6L).userId(userId).name("Vorlage")
                .isTemplate(true).createdAt(LocalDateTime.now()).build();
        when(aclService.hasCollectionsPermission(userId, AclPermission.MANAGE_TEMPLATES)).thenReturn(true);
        when(collectionRepo.save(any())).thenReturn(saved);
        when(itemRepo.countByCollectionIdAndResourceIdIsNotNull(6L)).thenReturn(0L);

        CollectionDto dto = service.create(userId, UserRole.ADMIN, "Vorlage", true);

        assertThat(dto.isTemplate()).isTrue();
    }

    @Test
    void create_withoutTemplatePermissionWithTemplateFlag_throws403() {
        when(aclService.hasCollectionsPermission(userId, AclPermission.MANAGE_TEMPLATES)).thenReturn(false);

        assertThatThrownBy(() -> service.create(userId, UserRole.USER, "Vorlage", true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(collectionRepo, never()).save(any());
    }

    @Test
    void listTemplates_returnsOnlyTemplatesOrderedByName() {
        Collection t1 = Collection.builder().id(20L).userId(otherUserId).name("Basis-Vorlage")
                .isTemplate(true).createdAt(LocalDateTime.now()).build();
        when(collectionRepo.findByIsTemplateTrueOrderByNameAsc()).thenReturn(List.of(t1));
        when(itemRepo.countByCollectionIdAndResourceIdIsNotNull(20L)).thenReturn(4L);

        List<CollectionDto> result = service.listTemplates();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Basis-Vorlage");
        assertThat(result.getFirst().isTemplate()).isTrue();
    }

    @Test
    void createFromTemplate_copiesResourceAndTestimonialItems() {
        Collection template = Collection.builder().id(20L).userId(otherUserId).name("Vorlage").isTemplate(true).build();
        Collection created = Collection.builder().id(30L).userId(userId).name("Meine Kopie").createdAt(LocalDateTime.now()).build();
        CollectionItem resourceItem = CollectionItem.builder().collectionId(20L).resourceId(1L).build();
        CollectionItem testimonialItem = CollectionItem.builder().collectionId(20L).testimonialId(2L).build();

        when(collectionRepo.findById(20L)).thenReturn(Optional.of(template));
        when(collectionRepo.save(any())).thenReturn(created);
        when(itemRepo.findByCollectionId(20L)).thenReturn(List.of(resourceItem, testimonialItem));
        when(itemRepo.countByCollectionIdAndResourceIdIsNotNull(30L)).thenReturn(1L);

        CollectionDto dto = service.createFromTemplate(userId, 20L, "Meine Kopie");

        assertThat(dto.id()).isEqualTo(30L);
        ArgumentCaptor<List<CollectionItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepo).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).allMatch(item -> item.getCollectionId().equals(30L));
        assertThat(captor.getValue()).anyMatch(item -> Long.valueOf(1L).equals(item.getResourceId()));
        assertThat(captor.getValue()).anyMatch(item -> Long.valueOf(2L).equals(item.getTestimonialId()));
    }

    @Test
    void copy_preservesIsTemplateFlag() {
        Collection source = Collection.builder().id(10L).userId(userId).name("Vorlage").isTemplate(true).build();
        Collection copied = Collection.builder().id(30L).userId(userId).name("Kopie").isTemplate(true).createdAt(LocalDateTime.now()).build();

        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(source));
        when(collectionRepo.existsByUserIdAndNameIgnoreCase(userId, "Kopie")).thenReturn(false);
        when(collectionRepo.save(any())).thenReturn(copied);
        when(itemRepo.findByCollectionId(10L)).thenReturn(List.of());
        when(itemRepo.countByCollectionIdAndResourceIdIsNotNull(30L)).thenReturn(0L);

        service.copy(userId, UserRole.USER, 10L, "Kopie");

        ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
        verify(collectionRepo).save(captor.capture());
        assertThat(captor.getValue().isTemplate()).isTrue();
    }

    @Test
    void createFromTemplate_nonTemplateSource_throws404() {
        Collection notTemplate = Collection.builder().id(20L).userId(otherUserId).name("Privat").isTemplate(false).build();
        when(collectionRepo.findById(20L)).thenReturn(Optional.of(notTemplate));

        assertThatThrownBy(() -> service.createFromTemplate(userId, 20L, "Kopie"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(collectionRepo, never()).save(any());
    }

    @Test
    void rename_ownerCanRename() {
        Collection c = Collection.builder().id(10L).userId(userId).name("Alt").createdAt(LocalDateTime.now()).build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(collectionRepo.save(any())).thenReturn(c);
        when(itemRepo.countByCollectionIdAndResourceIdIsNotNull(10L)).thenReturn(0L);

        CollectionDto dto = service.rename(userId, UserRole.USER, 10L, "Neu", null);
        assertThat(dto.name()).isEqualTo("Neu");
    }

    @Test
    void rename_foreignCollection_throws403() {
        when(collectionRepo.findByIdAndUserId(10L, otherUserId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rename(otherUserId, UserRole.USER, 10L, "x", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void rename_adminCanRenameTemplateNotOwnedByThem() {
        Collection template = Collection.builder().id(10L).userId(otherUserId).name("Vorlage")
                .isTemplate(true).createdAt(LocalDateTime.now()).build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.empty());
        when(collectionRepo.findById(10L)).thenReturn(Optional.of(template));
        when(collectionRepo.save(any())).thenReturn(template);
        when(itemRepo.countByCollectionIdAndResourceIdIsNotNull(10L)).thenReturn(0L);

        CollectionDto dto = service.rename(userId, UserRole.ADMIN, 10L, "Neuer Name", null);
        assertThat(dto).isNotNull();
    }

    @Test
    void rename_adminCannotRenameForeignNonTemplateCollection() {
        Collection notTemplate = Collection.builder().id(10L).userId(otherUserId).name("Privat").isTemplate(false).build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.empty());
        when(collectionRepo.findById(10L)).thenReturn(Optional.of(notTemplate));

        assertThatThrownBy(() -> service.rename(userId, UserRole.ADMIN, 10L, "x", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void rename_setsTemplateWhenPermitted() {
        Collection c = Collection.builder().id(10L).userId(userId).name("Alt").isTemplate(false).createdAt(LocalDateTime.now()).build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(aclService.hasCollectionsPermission(userId, AclPermission.MANAGE_TEMPLATES)).thenReturn(true);
        when(collectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepo.countByCollectionIdAndResourceIdIsNotNull(10L)).thenReturn(0L);

        CollectionDto dto = service.rename(userId, UserRole.USER, 10L, "Alt", true);

        assertThat(dto.isTemplate()).isTrue();
    }

    @Test
    void rename_removesTemplateWhenPermitted() {
        Collection c = Collection.builder().id(10L).userId(userId).name("Alt").isTemplate(true).createdAt(LocalDateTime.now()).build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(aclService.hasCollectionsPermission(userId, AclPermission.MANAGE_TEMPLATES)).thenReturn(true);
        when(collectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepo.countByCollectionIdAndResourceIdIsNotNull(10L)).thenReturn(0L);

        CollectionDto dto = service.rename(userId, UserRole.USER, 10L, "Alt", false);

        assertThat(dto.isTemplate()).isFalse();
    }

    @Test
    void rename_withoutPermissionSettingTemplateFlag_throws403() {
        Collection c = Collection.builder().id(10L).userId(userId).name("Alt").isTemplate(false).createdAt(LocalDateTime.now()).build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(aclService.hasCollectionsPermission(userId, AclPermission.MANAGE_TEMPLATES)).thenReturn(false);

        assertThatThrownBy(() -> service.rename(userId, UserRole.USER, 10L, "Alt", true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(collectionRepo, never()).save(any());
    }

    @Test
    void rename_withoutPermissionRemovingTemplateFlag_throws403() {
        // Bewusste Asymmetrie zu create() (siehe Spec): auch das "Entwidmen" einer
        // Vorlage (true -> false) erfordert MANAGE_TEMPLATES, nicht nur das Setzen.
        Collection c = Collection.builder().id(10L).userId(userId).name("Alt").isTemplate(true).createdAt(LocalDateTime.now()).build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(aclService.hasCollectionsPermission(userId, AclPermission.MANAGE_TEMPLATES)).thenReturn(false);

        assertThatThrownBy(() -> service.rename(userId, UserRole.USER, 10L, "Alt", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(collectionRepo, never()).save(any());
    }

    @Test
    void rename_adminChangesTemplateFlagOnForeignTemplate() {
        Collection template = Collection.builder().id(10L).userId(otherUserId).name("Vorlage").isTemplate(true).createdAt(LocalDateTime.now()).build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.empty());
        when(collectionRepo.findById(10L)).thenReturn(Optional.of(template));
        when(aclService.hasCollectionsPermission(userId, AclPermission.MANAGE_TEMPLATES)).thenReturn(true);
        when(collectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepo.countByCollectionIdAndResourceIdIsNotNull(10L)).thenReturn(0L);

        CollectionDto dto = service.rename(userId, UserRole.ADMIN, 10L, "Vorlage", false);

        assertThat(dto.isTemplate()).isFalse();
    }

    @Test
    void rename_sameTemplateValueWithoutPermission_noOpIsAllowed() {
        Collection c = Collection.builder().id(10L).userId(userId).name("Alt").isTemplate(true).createdAt(LocalDateTime.now()).build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(collectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepo.countByCollectionIdAndResourceIdIsNotNull(10L)).thenReturn(0L);

        // isTemplate bleibt true -> keine tatsächliche Änderung -> keine Berechtigungsprüfung nötig
        CollectionDto dto = service.rename(userId, UserRole.USER, 10L, "Alt", true);

        assertThat(dto.isTemplate()).isTrue();
        verify(aclService, never()).hasCollectionsPermission(any(), any());
    }

    @Test
    void delete_ownerCanDelete() {
        Collection c = Collection.builder().id(10L).userId(userId).name("T").build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));

        service.delete(userId, UserRole.USER, 10L);
        verify(collectionRepo).delete(c);
    }

    @Test
    void delete_foreignCollection_throws403() {
        when(collectionRepo.findByIdAndUserId(10L, otherUserId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(otherUserId, UserRole.USER, 10L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void addItem_validResource_addsItem() {
        Collection c = Collection.builder().id(10L).userId(userId).name("T").build();
        Resource r = new Resource();
        r.setId(99L);
        r.setFolderId(null);
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(resourceRepo.findById(99L)).thenReturn(Optional.of(r));
        when(itemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addItem(userId, UserRole.USER, 10L, 99L);
        verify(itemRepo).save(any());
    }

    @Test
    void addItem_duplicate_ignored() {
        Collection c = Collection.builder().id(10L).userId(userId).name("T").build();
        Resource r = new Resource();
        r.setId(99L);
        r.setFolderId(null);
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(resourceRepo.findById(99L)).thenReturn(Optional.of(r));
        when(itemRepo.existsByCollectionIdAndResourceId(10L, 99L)).thenReturn(true);

        // soll keinen Fehler werfen und keinen (erneuten) Insert versuchen
        service.addItem(userId, UserRole.USER, 10L, 99L);
        verify(itemRepo, never()).save(any());
    }

    @Test
    void listItems_filtersResourcesWithRevokedPermission() {
        Collection c = Collection.builder().id(10L).userId(userId).name("T").build();
        CollectionItem item1 = CollectionItem.builder().collectionId(10L).resourceId(1L).build();
        CollectionItem item2 = CollectionItem.builder().collectionId(10L).resourceId(2L).build();

        Resource r1 = new Resource(); r1.setId(1L); r1.setFolderId(100L);
        Resource r2 = new Resource(); r2.setId(2L); r2.setFolderId(200L);

        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(itemRepo.findByCollectionIdAndResourceIdIsNotNullOrderByAddedAtDesc(10L)).thenReturn(List.of(item1, item2));
        when(resourceRepo.findAllById(any())).thenReturn(List.of(r1, r2));
        when(aclService.hasPermission(userId, 100L, AclResourceType.FOLDER, AclPermission.READ)).thenReturn(true);
        when(aclService.hasPermission(userId, 200L, AclResourceType.FOLDER, AclPermission.READ)).thenReturn(false);

        ResourceDto dto1 = mock(ResourceDto.class);
        when(resourceService.toDtoList(List.of(r1))).thenReturn(List.of(dto1));

        var result = service.listItems(userId, UserRole.USER, 10L);
        assertThat(result).hasSize(1);
        verify(resourceService).toDtoList(List.of(r1));
    }

    @Test
    void addTestimonialItem_validTestimonial_addsItem() {
        Collection c = Collection.builder().id(10L).userId(userId).name("T").build();
        Testimonial t = Testimonial.builder().id(50L).authorName("A").text("Text").userId(userId).build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(aclService.hasTestimonialsPermission(userId, AclPermission.READ)).thenReturn(true);
        when(testimonialRepo.findById(50L)).thenReturn(Optional.of(t));
        when(itemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addTestimonialItem(userId, UserRole.USER, 10L, 50L);
        verify(itemRepo).save(any());
    }

    @Test
    void addTestimonialItem_noReadPermission_throws403() {
        Collection c = Collection.builder().id(10L).userId(userId).name("T").build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(aclService.hasTestimonialsPermission(userId, AclPermission.READ)).thenReturn(false);

        assertThatThrownBy(() -> service.addTestimonialItem(userId, UserRole.USER, 10L, 50L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(itemRepo, never()).save(any());
    }

    @Test
    void addTestimonialItem_duplicate_ignored() {
        Collection c = Collection.builder().id(10L).userId(userId).name("T").build();
        Testimonial t = Testimonial.builder().id(50L).authorName("A").text("Text").userId(userId).build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(aclService.hasTestimonialsPermission(userId, AclPermission.READ)).thenReturn(true);
        when(testimonialRepo.findById(50L)).thenReturn(Optional.of(t));
        when(itemRepo.existsByCollectionIdAndTestimonialId(10L, 50L)).thenReturn(true);

        // soll keinen Fehler werfen und keinen (erneuten) Insert versuchen
        service.addTestimonialItem(userId, UserRole.USER, 10L, 50L);
        verify(itemRepo, never()).save(any());
    }

    @Test
    void removeTestimonialItem_removesWhenPresent() {
        Collection c = Collection.builder().id(10L).userId(userId).name("T").build();
        CollectionItem item = CollectionItem.builder().id(1L).collectionId(10L).testimonialId(50L).build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(itemRepo.findByCollectionIdAndTestimonialId(10L, 50L)).thenReturn(Optional.of(item));

        service.removeTestimonialItem(userId, UserRole.USER, 10L, 50L);
        verify(itemRepo).delete(item);
    }

    @Test
    void listTestimonialItems_returnsMappedDtosInAddedOrder() {
        Collection c = Collection.builder().id(10L).userId(userId).name("T").build();
        CollectionItem item1 = CollectionItem.builder().collectionId(10L).testimonialId(1L).build();
        CollectionItem item2 = CollectionItem.builder().collectionId(10L).testimonialId(2L).build();
        TestimonialDto dto1 = new TestimonialDto(1L, "A", null, "textA", true, false, userId, LocalDateTime.now(), LocalDateTime.now(), List.of());
        TestimonialDto dto2 = new TestimonialDto(2L, "B", null, "textB", true, false, userId, LocalDateTime.now(), LocalDateTime.now(), List.of());

        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(aclService.hasTestimonialsPermission(userId, AclPermission.READ)).thenReturn(true);
        when(itemRepo.findByCollectionIdAndTestimonialIdIsNotNullOrderByAddedAtDesc(10L)).thenReturn(List.of(item1, item2));
        when(testimonialService.getByIds(List.of(1L, 2L))).thenReturn(List.of(dto1, dto2));

        List<TestimonialDto> result = service.listTestimonialItems(userId, UserRole.USER, 10L);

        assertThat(result).extracting(TestimonialDto::id).containsExactly(1L, 2L);
    }

    @Test
    void listTestimonialItems_noReadPermission_returnsEmptyList() {
        Collection c = Collection.builder().id(10L).userId(userId).name("T").build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(aclService.hasTestimonialsPermission(userId, AclPermission.READ)).thenReturn(false);

        List<TestimonialDto> result = service.listTestimonialItems(userId, UserRole.USER, 10L);

        assertThat(result).isEmpty();
        verify(itemRepo, never()).findByCollectionIdAndTestimonialIdIsNotNullOrderByAddedAtDesc(any());
    }

    @Test
    void requireTestimonialIdsForReport_ownerWithReadPermission_returnsIdsInAddedOrder() {
        Collection c = Collection.builder().id(10L).userId(userId).name("T").build();
        CollectionItem item1 = CollectionItem.builder().collectionId(10L).testimonialId(1L).build();
        CollectionItem item2 = CollectionItem.builder().collectionId(10L).testimonialId(2L).build();

        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(aclService.hasTestimonialsPermission(userId, AclPermission.READ)).thenReturn(true);
        when(itemRepo.findByCollectionIdAndTestimonialIdIsNotNullOrderByAddedAtDesc(10L))
                .thenReturn(List.of(item1, item2));

        List<Long> result = service.requireTestimonialIdsForReport(userId, UserRole.USER, 10L);

        assertThat(result).containsExactly(1L, 2L);
    }

    @Test
    void requireTestimonialIdsForReport_noReadPermission_returnsEmptyList() {
        Collection c = Collection.builder().id(10L).userId(userId).name("T").build();
        when(collectionRepo.findByIdAndUserId(10L, userId)).thenReturn(Optional.of(c));
        when(aclService.hasTestimonialsPermission(userId, AclPermission.READ)).thenReturn(false);

        List<Long> result = service.requireTestimonialIdsForReport(userId, UserRole.USER, 10L);

        assertThat(result).isEmpty();
        verify(itemRepo, never()).findByCollectionIdAndTestimonialIdIsNotNullOrderByAddedAtDesc(any());
    }

    @Test
    void requireTestimonialIdsForReport_foreignCollection_throws403() {
        when(collectionRepo.findByIdAndUserId(10L, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireTestimonialIdsForReport(otherUserId, UserRole.USER, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(itemRepo, never()).findByCollectionIdAndTestimonialIdIsNotNullOrderByAddedAtDesc(any());
    }
}
