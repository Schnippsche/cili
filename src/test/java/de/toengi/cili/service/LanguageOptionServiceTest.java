package de.toengi.cili.service;

import de.toengi.cili.dto.language.LanguageOptionDto;
import de.toengi.cili.model.entity.LanguageOption;
import de.toengi.cili.repository.LanguageOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageOptionServiceTest {

    @Mock
    private LanguageOptionRepository languageOptionRepository;

    @InjectMocks
    private LanguageOptionService languageOptionService;

    private LanguageOption de;
    private LanguageOption en;
    private LanguageOption noTrans;

    @BeforeEach
    void setUp() {
        de = LanguageOption.builder().code("de").label("Deutsch")
                .translationSupported(true).sortOrder(10).enabled(true).build();
        en = LanguageOption.builder().code("en").label("English")
                .translationSupported(true).sortOrder(20).enabled(true).build();
        noTrans = LanguageOption.builder().code("ar").label("Arabic")
                .translationSupported(false).sortOrder(30).enabled(true).build();
    }

    @Test
    void findAll_returnsMappedDtos() {
        when(languageOptionRepository.findByEnabledTrueOrderBySortOrder())
                .thenReturn(List.of(de, en));

        List<LanguageOptionDto> result = languageOptionService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isEqualTo("de");
        assertThat(result.get(0).label()).isEqualTo("Deutsch");
        assertThat(result.get(0).translationSupported()).isTrue();
    }

    @Test
    void findAll_returnsEmptyListWhenTableEmpty() {
        when(languageOptionRepository.findByEnabledTrueOrderBySortOrder())
                .thenReturn(List.of());

        assertThat(languageOptionService.findAll()).isEmpty();
    }

    @Test
    void getSupportedTranslationCodes_excludesNonTranslationLanguages() {
        when(languageOptionRepository.findByEnabledTrueOrderBySortOrder())
                .thenReturn(List.of(de, en, noTrans));

        Set<String> codes = languageOptionService.getSupportedTranslationCodes();

        assertThat(codes).containsExactlyInAnyOrder("de", "en").doesNotContain("ar");
    }

    @Test
    void getSupportedTranslationCodes_returnsEmptySetWhenNoneSupported() {
        when(languageOptionRepository.findByEnabledTrueOrderBySortOrder())
                .thenReturn(List.of(noTrans));

        assertThat(languageOptionService.getSupportedTranslationCodes()).isEmpty();
    }

    @Test
    void getLabelForCode_returnsLabelWhenCodeExists() {
        when(languageOptionRepository.findById("de")).thenReturn(Optional.of(de));

        assertThat(languageOptionService.getLabelForCode("de")).isEqualTo("Deutsch");
    }

    @Test
    void getLabelForCode_returnsCodeAsFallbackWhenNotFound() {
        when(languageOptionRepository.findById("xx")).thenReturn(Optional.empty());

        assertThat(languageOptionService.getLabelForCode("xx")).isEqualTo("xx");
    }
}
