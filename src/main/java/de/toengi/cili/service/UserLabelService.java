package de.toengi.cili.service;

import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.User;
import de.toengi.cili.repository.UserRepository;
import de.toengi.cili.util.CommandRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserLabelService {

    private static final String SCRIPT_NAME = "generate_labels.py";

    private final UserRepository userRepository;
    private final CommandRunner commandRunner;
    private final CiliGlobalConfig global;

    @Transactional(readOnly = true)
    public Path generateLabelSheet(Long userId) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!StringUtils.hasText(user.getUrl())) {
            throw new CiliException(
                    "Nutzer '" + user.getUsername() + "' hat keine URL hinterlegt – für den QR-Code wird eine URL benötigt.",
                    HttpStatus.CONFLICT);
        }

        Path output = Files.createTempFile("cili-labels-" + userId + "-", ".pdf");

        List<String> cmd = new ArrayList<>(List.of(
                global.getPythonPath(), global.resolve(SCRIPT_NAME),
                "--name", StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername(),
                "--email", user.getEmail(),
                "--url", user.getUrl(),
                "--output", output.toString()));
        if (user.getMemberId() != null) {
            cmd.add("--member-id");
            cmd.add(String.valueOf(user.getMemberId()));
        }
        if (StringUtils.hasText(user.getPhone())) {
            cmd.add("--phone");
            cmd.add(user.getPhone());
        }

        int exitCode;
        try {
            exitCode = commandRunner.run(cmd);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(output);
            throw new IOException("Etiketten-Generierung unterbrochen", e);
        }

        if (exitCode != 0 || !Files.exists(output)) {
            Files.deleteIfExists(output);
            throw new IllegalStateException("Etiketten-Generierung fehlgeschlagen (exit code " + exitCode + ")");
        }

        log.info("Etiketten-Bogen erzeugt für Nutzer id={} username='{}'", userId, user.getUsername());
        return output;
    }
}
