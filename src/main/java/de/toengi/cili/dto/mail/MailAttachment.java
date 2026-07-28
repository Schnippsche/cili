package de.toengi.cili.dto.mail;

public record MailAttachment(String filename, byte[] content, String contentType) {}
