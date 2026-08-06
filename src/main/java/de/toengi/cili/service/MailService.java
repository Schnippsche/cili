package de.toengi.cili.service;

import de.toengi.cili.config.MailConfig;
import de.toengi.cili.dto.mail.MailAttachment;
import de.toengi.cili.dto.mail.MailMessageRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailConfig mailConfig;

    @Async("mailExecutor")
    public void send(MailMessageRequest request) {
        try {
            sendSync(request);
        } catch (Exception e) {
            // Bewusst breiter Catch statt nur MessagingException|MailException: das Template-Rendering
            // (templateEngine.process) kann ungecheckte Thymeleaf-Exceptions werfen (z.B. bei fehlendem
            // Template unter templates/mail/), die sonst aus der @Async-void-Methode entweichen und beim
            // Default-AsyncUncaughtExceptionHandler unstrukturiert landen würden — das würde der
            // Zusicherung "keine Exception-Propagation" widersprechen.
            log.error("E-Mail-Versand fehlgeschlagen an {} (Betreff: {})", request.to(), request.subject(), e);
        }
    }

    /**
     * Wie {@link #send}, aber synchron und ohne Exception-Schlucken — für Aufrufer (z.B. den
     * Mailflow-Batch), die Erfolg/Fehlschlag pro Aufruf selbst auswerten müssen.
     */
    public void sendSync(MailMessageRequest request) throws MessagingException {
        if (!mailConfig.isEnabled()) {
            log.info("Mail-Versand deaktiviert (cili.mail.enabled=false) — E-Mail an {} wird nicht gesendet, Betreff: {}",
                request.to(), request.subject());
            return;
        }

        Context ctx = new Context(request.locale());
        request.variables().forEach(ctx::setVariable);
        String html = templateEngine.process("mail/" + request.templateName(), ctx);

        // multipart=true unabhängig von Attachments: erst das ermöglicht MimeMessageHelper#setText
        // mit Plain-Text-Alternative (multipart/alternative) statt nur text/html — HTML-only-Mails
        // ohne Text-Fallback werden von Spamfiltern spürbar schlechter bewertet.
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
        helper.setFrom(mailConfig.getFrom());
        helper.setTo(request.to().toArray(new String[0]));
        if (!request.cc().isEmpty()) helper.setCc(request.cc().toArray(new String[0]));
        if (!request.bcc().isEmpty()) helper.setBcc(request.bcc().toArray(new String[0]));
        helper.setSubject(request.subject());
        helper.setText(toPlainText(html), html);

        for (MailAttachment att : request.attachments()) {
            helper.addAttachment(att.filename(), new ByteArrayResource(att.content()), att.contentType());
        }

        // List-Unsubscribe (RFC 8058): ohne diesen Header stufen Gmail/Yahoo & Co. Massen-Mail mit
        // Abmeldelink im Body allein zunehmend als Spam ein.
        if (request.unsubscribeUrl() != null && !request.unsubscribeUrl().isBlank()) {
            mime.setHeader("List-Unsubscribe", "<" + request.unsubscribeUrl() + ">");
            mime.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        }

        mailSender.send(mime);
        log.info("E-Mail versendet an {} (Betreff: {})", request.to(), request.subject());
    }

    /**
     * Grobe HTML-zu-Text-Konvertierung für die Plain-Text-Alternative. Kein Anspruch auf ein
     * hübsches Layout — dient nur als Spamfilter-Fallback für Clients, die kein HTML rendern.
     */
    private String toPlainText(String html) {
        String withoutTags = html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n\n")
                .replaceAll("<[^>]+>", "");
        String decoded = withoutTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return decoded.replaceAll("[ \\t]+", " ").replaceAll("\n{3,}", "\n\n").trim();
    }
}
