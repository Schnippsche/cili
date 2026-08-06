package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cili.mail")
@Getter @Setter
public class MailConfig {
    /** Absenderadresse für alle über den MailService versendeten E-Mails. */
    private String from = "no-reply@cili.toengi.de";
    /** Schalter, um den tatsächlichen Versand zu deaktivieren (z.B. lokale Entwicklung ohne SMTP-Zugang). */
    private boolean enabled = true;
}
