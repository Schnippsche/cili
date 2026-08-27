package de.toengi.cili.dto.resource;

/**
 * Eine kürzlich hochgeladene Ressource für die Startseiten-Liste „Aktuellste Dateien".
 * Enthält die volle {@link ResourceDto} plus den Namen des enthaltenden Ordners für die Anzeige.
 */
public record RecentResourceDto(
        ResourceDto resource,
        String folderName
) {}
