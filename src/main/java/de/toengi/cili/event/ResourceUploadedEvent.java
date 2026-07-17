package de.toengi.cili.event;

public record ResourceUploadedEvent(Long resourceId, String mimeType, Long folderId) {}
