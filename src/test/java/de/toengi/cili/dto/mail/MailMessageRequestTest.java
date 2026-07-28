package de.toengi.cili.dto.mail;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MailMessageRequestTest {

    @Test
    void nullOptionalFields_areReplacedWithSafeDefaults() {
        MailMessageRequest request = new MailMessageRequest(
                List.of("to@example.com"), null, null, "Betreff", "welcome", null, null, null);

        assertThat(request.cc()).isEmpty();
        assertThat(request.bcc()).isEmpty();
        assertThat(request.variables()).isEmpty();
        assertThat(request.attachments()).isEmpty();
        assertThat(request.locale()).isEqualTo(Locale.GERMAN);
    }

    @Test
    void nonNullValues_arePassedThroughUnchanged() {
        MailMessageRequest request = new MailMessageRequest(
                List.of("to@example.com"), List.of("cc@example.com"), List.of("bcc@example.com"),
                "Betreff", "welcome", Map.of("name", "Alice"),
                List.of(new MailAttachment("a.txt", new byte[0], "text/plain")), Locale.ENGLISH);

        assertThat(request.cc()).containsExactly("cc@example.com");
        assertThat(request.locale()).isEqualTo(Locale.ENGLISH);
    }
}
