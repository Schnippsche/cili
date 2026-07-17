package de.toengi.cili.repository;

import de.toengi.cili.model.entity.LanguageOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LanguageOptionRepository extends JpaRepository<LanguageOption, String> {
    List<LanguageOption> findByEnabledTrueOrderBySortOrder();
}
