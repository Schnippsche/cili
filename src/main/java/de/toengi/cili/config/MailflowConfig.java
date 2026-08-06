package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConfigurationProperties(prefix = "cili.mailflows")
@Getter @Setter
public class MailflowConfig {

    /** Cron-Ausdruck für den nächtlichen Batch-Lauf — nur Pflicht, wenn flows nicht leer ist. */
    private String batchCron;

    /** Konfigurierte Mailflows, Schlüssel = flowName (z.B. "onboarding"). */
    private Map<String, FlowDefinition> flows = new LinkedHashMap<>();

    public Optional<FlowDefinition> findFlow(String flowName) {
        return Optional.ofNullable(flows.get(flowName));
    }

    public Optional<StepDefinition> findStep(String flowName, String stepId) {
        return findFlow(flowName)
                .flatMap(f -> f.getSteps().stream().filter(s -> stepId.equals(s.getId())).findFirst());
    }

    @Getter @Setter
    public static class FlowDefinition {
        private String description;
        private List<StepDefinition> steps = new ArrayList<>();
    }

    @Getter @Setter
    public static class StepDefinition {
        private String id;
        private int delayDays;
        /** Template für die Sie-Form (Pflicht, Fallback falls keine Du-Form gepflegt ist). */
        private String template;
        /** Template für die Du-Form (optional). Fällt auf {@link #template} zurück, wenn nicht gesetzt. */
        private String templateInformal;
        private String subject;
        private String attachment;
    }
}
