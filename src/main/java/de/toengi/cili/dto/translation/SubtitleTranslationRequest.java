package de.toengi.cili.dto.translation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubtitleTranslationRequest(
    @NotNull Long sourceTrackId,
    @NotBlank String targetLang) {}
