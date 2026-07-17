package de.toengi.cili.repository;

import de.toengi.cili.model.entity.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollectionRepository extends JpaRepository<Collection, Long> {

    List<Collection> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Collection> findByIdAndUserId(Long id, Long userId);

    List<Collection> findByIsTemplateTrueOrderByNameAsc();

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long excludeId);
}
