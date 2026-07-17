package de.toengi.cili.service;

import de.toengi.cili.config.VideoImportConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CookiesExpiryService {

    private static final Set<String> AUTH_COOKIE_NAMES = Set.of(
        "SID", "HSID", "SSID", "APISID", "SAPISID",
        "__Secure-1PSID", "__Secure-3PSID", "__Secure-1PAPISID", "__Secure-3PAPISID",
        "LOGIN_INFO"
    );
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final VideoImportConfig config;

    @EventListener(ApplicationReadyEvent.class)
    public void logOnStartup() {
        logStatus();
    }

    public void logStatus() {
        String cookiesFile = config.getCookiesFile();
        if (cookiesFile == null || cookiesFile.isBlank()) {
            log.warn("[cookies] Keine Cookies-Datei konfiguriert (cili.video-import.cookies-file). YouTube-Downloads werden fehlschlagen.");
            return;
        }
        Path path = Path.of(cookiesFile);
        if (!Files.exists(path)) {
            log.warn("[cookies] Cookies-Datei nicht gefunden: {}", path);
            return;
        }
        Optional<Instant> expiry = earliestAuthCookieExpiry(path);
        if (expiry.isEmpty()) {
            log.warn("[cookies] Keine YouTube-Auth-Cookies in {} gefunden.", path);
            return;
        }
        Instant exp = expiry.get();
        long daysLeft = (exp.getEpochSecond() - Instant.now().getEpochSecond()) / 86400;
        if (daysLeft < 0) {
            log.error("[cookies] YouTube-Cookies ABGELAUFEN seit {} Tagen ({}). Bitte erneuern.",
                Math.abs(daysLeft), FMT.format(exp));
        } else if (daysLeft < 14) {
            log.warn("[cookies] YouTube-Cookies laufen in {} Tagen ab ({}).", daysLeft, FMT.format(exp));
        } else {
            log.info("[cookies] YouTube-Cookies gültig bis {} (noch {} Tage).", FMT.format(exp), daysLeft);
        }
    }

    private Optional<Instant> earliestAuthCookieExpiry(Path path) {
        try {
            return Files.lines(path)
                .filter(l -> !l.startsWith("#") && !l.isBlank())
                .filter(l -> {
                    String[] f = l.split("\t");
                    if (f.length < 7) return false;
                    String domain = f[0];
                    String name   = f[5];
                    return (domain.contains("youtube.com") || domain.contains("google.com"))
                        && AUTH_COOKIE_NAMES.contains(name);
                })
                .map(l -> {
                    String[] f = l.split("\t");
                    try { return Long.parseLong(f[4].trim()); } catch (NumberFormatException e) { return 0L; }
                })
                .filter(ts -> ts > 0)
                .min(Long::compareTo)
                .map(Instant::ofEpochSecond);
        } catch (IOException e) {
            log.warn("[cookies] Fehler beim Lesen der Cookies-Datei: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
