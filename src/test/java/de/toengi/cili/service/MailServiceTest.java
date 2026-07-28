package de.toengi.cili.service;

import de.toengi.cili.config.MailConfig;
import de.toengi.cili.dto.mail.MailAttachment;
import de.toengi.cili.dto.mail.MailMessageRequest;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateInputException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private TemplateEngine templateEngine;

    private MailConfig mailConfig;
    private MailService mailService;

    @BeforeEach
    void setUp() {
        mailConfig = new MailConfig();
        mailConfig.setFrom("no-reply@cili.toengi.de");
        mailConfig.setEnabled(true);
        mailService = new MailService(mailSender, templateEngine, mailConfig);
    }

    @Test
    void send_whenDisabled_doesNotCallMailSender() {
        mailConfig.setEnabled(false);
        MailMessageRequest request = new MailMessageRequest(
                List.of("user@example.com"), null, null, "Betreff", "welcome", null, null, null);

        mailService.send(request);

        verifyNoInteractions(mailSender);
    }

    @Test
    void send_whenEnabled_buildsMimeMessageWithAllFields() throws Exception {
        MimeMessage realMime = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(realMime);
        when(templateEngine.process(eq("mail/welcome"), any(Context.class))).thenReturn("<p>Hallo</p>");

        MailMessageRequest request = new MailMessageRequest(
                List.of("to@example.com"),
                List.of("cc@example.com"),
                List.of("bcc@example.com"),
                "Willkommen",
                "welcome",
                Map.of("name", "Alice"),
                List.of(new MailAttachment("info.txt", "Inhalt".getBytes(StandardCharsets.UTF_8), "text/plain")),
                null);

        mailService.send(request);

        // MimeMessage.setDataHandler(...) entfernt den Content-Type-Header und setzt ihn erst in
        // saveChanges() neu (aus dem DataHandler abgeleitet). Ein echter JavaMailSenderImpl.send(...)
        // ruft saveChanges() intern auf; da mailSender hier gemockt ist (send() ist ein No-Op),
        // muss der Test das für die Inhaltsprüfung selbst nachholen.
        realMime.saveChanges();

        verify(mailSender).send(realMime);
        assertThat(realMime.getAllRecipients()).hasSize(3);
        assertThat(realMime.getSubject()).isEqualTo("Willkommen");
        assertThat(realMime.getFrom()[0].toString()).contains("no-reply@cili.toengi.de");
        assertThat(realMime.getContentType()).contains("multipart/mixed");
    }

    @Test
    void send_whenTemplateRenderingFails_logsAndDoesNotThrow() {
        when(templateEngine.process(anyString(), any(Context.class)))
                .thenThrow(new TemplateInputException("Template nicht gefunden"));

        MailMessageRequest request = new MailMessageRequest(
                List.of("to@example.com"), null, null, "Betreff", "missing-template", null, null, null);

        assertThatCode(() -> mailService.send(request)).doesNotThrowAnyException();
        verifyNoInteractions(mailSender);
    }

    @Test
    void send_whenMailSenderThrows_logsAndDoesNotThrow() throws Exception {
        MimeMessage realMime = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(realMime);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<p>Hallo</p>");
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        MailMessageRequest request = new MailMessageRequest(
                List.of("to@example.com"), null, null, "Betreff", "welcome", null, null, null);

        assertThatCode(() -> mailService.send(request)).doesNotThrowAnyException();
    }
}
