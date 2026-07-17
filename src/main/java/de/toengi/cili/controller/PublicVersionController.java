package de.toengi.cili.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/version")
public class PublicVersionController {

    @Value("${cili.version:dev}")
    private String version;

    public record VersionResponse(String version) {}

    @GetMapping
    public VersionResponse get() {
        return new VersionResponse(version);
    }
}
