package de.toengi.cili.dto.mail;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public record MailMessageRequest(
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String subject,
    String templateName,
    Map<String, Object> variables,
    List<MailAttachment> attachments,
    Locale locale,
    /** Falls gesetzt: absolute URL für den List-Unsubscribe-Header (RFC 8058). */
    String unsubscribeUrl
) {
    // Compact Constructor: erzwingt Null-Sicherheit statt sich auf Aufrufer-Disziplin zu verlassen —
    // verhindert NPEs in MailService.send() bei request.variables().forEach(...) etc.
    public MailMessageRequest {
        cc = cc == null ? List.of() : cc;
        bcc = bcc == null ? List.of() : bcc;
        variables = variables == null ? Map.of() : variables;
        attachments = attachments == null ? List.of() : attachments;
        locale = locale == null ? Locale.GERMAN : locale;
    }
}
