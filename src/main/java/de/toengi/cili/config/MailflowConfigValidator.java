package de.toengi.cili.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@RequiredArgsConstructor
public class MailflowConfigValidator {

    private final MailflowConfig config;
    private final ResourceLoader resourceLoader;

    @PostConstruct
    public void validate() {
        if (config.getFlows().isEmpty()) {
            return;
        }
        Assert.hasText(config.getBatchCron(),
                () -> "cili.mailflows: batch-cron fehlt, obwohl flows konfiguriert sind");
        Assert.isTrue(CronExpression.isValidExpression(config.getBatchCron()),
                () -> "cili.mailflows.batch-cron ist kein gültiger Cron-Ausdruck: " + config.getBatchCron());

        config.getFlows().forEach((flowName, flow) -> {
            Assert.notEmpty(flow.getSteps(), () -> "cili.mailflows.flows." + flowName + ": steps ist leer");
            flow.getSteps().forEach(step -> validateStep(flowName, step));
        });
    }

    private void validateStep(String flowName, MailflowConfig.StepDefinition step) {
        String ctx = "cili.mailflows.flows." + flowName + ".steps";
        Assert.hasText(step.getId(), () -> ctx + ": id fehlt");
        Assert.hasText(step.getTemplate(), () -> ctx + " (" + step.getId() + "): template fehlt");
        Assert.hasText(step.getSubject(), () -> ctx + " (" + step.getId() + "): subject fehlt");

        String templatePath = "classpath:/templates/mail/" + step.getTemplate() + ".html";
        Assert.isTrue(resourceLoader.getResource(templatePath).exists(),
                () -> ctx + " (" + step.getId() + "): Template nicht gefunden: " + templatePath);

        if (step.getAttachment() != null) {
            String attachmentPath = "classpath:/mail-attachments/" + step.getAttachment();
            Assert.isTrue(resourceLoader.getResource(attachmentPath).exists(),
                    () -> ctx + " (" + step.getId() + "): Attachment nicht gefunden: " + attachmentPath);
        }
    }
}
