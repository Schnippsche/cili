package de.toengi.cili.controller;

import de.toengi.cili.dto.language.LanguageOptionDto;
import de.toengi.cili.service.LanguageOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LanguageOptionController {

    private final LanguageOptionService languageOptionService;

    @GetMapping("/api/languages")
    public List<LanguageOptionDto> list() {
        return languageOptionService.findAll();
    }
}
