package de.toengi.cili.repository;

import de.toengi.cili.model.entity.RightsGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RightsGroupRepository extends JpaRepository<RightsGroup, Long> {
    boolean existsByName(String name);
}
