package de.toengi.cili.service;

import de.toengi.cili.dto.language.LanguageOptionDto;
import de.toengi.cili.model.entity.LanguageOption;
import de.toengi.cili.repository.LanguageOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LanguageOptionService {

    private final LanguageOptionRepository languageOptionRepository;

    @Transactional(readOnly = true)
    public List<LanguageOptionDto> findAll() {
        return languageOptionRepository.findByEnabledTrueOrderBySortOrder()
                .stream()
                .map(l -> new LanguageOptionDto(l.getCode(), l.getLabel(), l.isTranslationSupported()))
                .toList();
    }

    @Transactional(readOnly = true)
    public String getLabelForCode(String code) {
        return languageOptionRepository.findById(code)
                .map(LanguageOption::getLabel)
                .orElse(code);
    }

    @Transactional(readOnly = true)
    public Set<String> getSupportedTranslationCodes() {
        return languageOptionRepository.findByEnabledTrueOrderBySortOrder()
                .stream()
                .filter(LanguageOption::isTranslationSupported)
                .map(LanguageOption::getCode)
                .collect(Collectors.toSet());
    }
}
