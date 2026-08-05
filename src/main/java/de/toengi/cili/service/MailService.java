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

        boolean hasAttachments = !request.attachments().isEmpty();
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, hasAttachments, "UTF-8");
        helper.setFrom(mailConfig.getFrom());
        helper.setTo(request.to().toArray(new String[0]));
        if (!request.cc().isEmpty()) helper.setCc(request.cc().toArray(new String[0]));
        if (!request.bcc().isEmpty()) helper.setBcc(request.bcc().toArray(new String[0]));
        helper.setSubject(request.subject());
        helper.setText(html, true);

        for (MailAttachment att : request.attachments()) {
            helper.addAttachment(att.filename(), new ByteArrayResource(att.content()), att.contentType());
        }

        mailSender.send(mime);
        log.info("E-Mail versendet an {} (Betreff: {})", request.to(), request.subject());
    }
}
