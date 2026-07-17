package de.toengi.cili.dto.translation;

import jakarta.validation.constraints.NotBlank;

public record DocumentTranslationRequest(
    @NotBlank String sourceLang,
    @NotBlank String targetLang) {}
