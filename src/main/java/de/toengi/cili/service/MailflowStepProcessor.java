package de.toengi.cili.service;

import de.toengi.cili.config.MailflowConfig;
import de.toengi.cili.dto.mail.MailAttachment;
import de.toengi.cili.dto.mail.MailMessageRequest;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Customer;
import de.toengi.cili.model.entity.MailflowInstance;
import de.toengi.cili.model.entity.MailflowStepStatus;
import de.toengi.cili.model.entity.User;
import de.toengi.cili.model.enums.MailflowStepState;
import de.toengi.cili.repository.CustomerRepository;
import de.toengi.cili.repository.MailflowInstanceRepository;
import de.toengi.cili.repository.MailflowStepStatusRepository;
import de.toengi.cili.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MailflowStepProcessor {

    private static final int MAX_ATTEMPTS = 3;

    private final MailflowStepStatusRepository stepRepository;
    private final MailflowInstanceRepository instanceRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final MailflowConfig config;
    private final MailService mailService;
    private final TransactionTemplate transactionTemplate;

    public MailflowStepProcessor(MailflowStepStatusRepository stepRepository,
                                  MailflowInstanceRepository instanceRepository,
                                  CustomerRepository customerRepository,
                                  UserRepository userRepository,
                                  MailflowConfig config,
                                  MailService mailService,
                                  PlatformTransactionManager transactionManager) {
        this.stepRepository = stepRepository;
        this.instanceRepository = instanceRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.config = config;
        this.mailService = mailService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void processDueSteps() {
        LocalDate today = LocalDate.now();
        List<Long> dueStepIds = stepRepository.findDueSteps(today).stream()
                .map(MailflowStepStatus::getId)
                .toList();
        log.info("Mailflow-Batch: {} fällige(r) Step(s) gefunden", dueStepIds.size());
        for (Long stepId : dueStepIds) {
            transactionTemplate.executeWithoutResult(status -> processStep(stepId));
        }
    }

    void processStep(Long stepId) {
        MailflowStepStatus step = stepRepository.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("MailflowStepStatus", stepId));
        MailflowInstance instance = instanceRepository.findById(step.getInstanceId())
                .orElseThrow(() -> new ResourceNotFoundException("MailflowInstance", step.getInstanceId()));
        Customer customer = customerRepository.findById(instance.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", instance.getCustomerId()));

        if (!customer.isConsentGranted()) {
            step.setStatus(MailflowStepState.SKIPPED);
            stepRepository.save(step);
            return;
        }

        MailflowConfig.StepDefinition stepDef = config.findStep(instance.getFlowName(), step.getStepId())
                .orElse(null);
        if (stepDef == null) {
            step.setStatus(MailflowStepState.FAILED);
            step.setLastError("Step-Definition nicht gefunden");
            stepRepository.save(step);
            return;
        }

        User sponsor = userRepository.findById(customer.getSponsorUserId()).orElse(null);
        MailMessageRequest request = buildRequest(customer, sponsor, stepDef);

        try {
            mailService.sendSync(request);
            step.setStatus(MailflowStepState.SENT);
            step.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            int attempts = step.getAttemptCount() + 1;
            step.setAttemptCount(attempts);
            step.setLastError(truncate(e.getMessage(), 1000));
            step.setStatus(attempts >= MAX_ATTEMPTS ? MailflowStepState.FAILED : MailflowStepState.ERROR);
        }
        stepRepository.save(step);
    }

    private MailMessageRequest buildRequest(Customer customer, User sponsor, MailflowConfig.StepDefinition stepDef) {
        List<MailAttachment> attachments = stepDef.getAttachment() == null
                ? List.of()
                : List.of(loadAttachment(stepDef.getAttachment()));
        String unsubscribeUrl = "/api/public/customers/unsubscribe/" + customer.getUnsubscribeToken();

        return new MailMessageRequest(
                List.of(customer.getEmail()),
                null,
                null,
                stepDef.getSubject(),
                stepDef.getTemplate(),
                Map.of("customer", customer, "sponsor", sponsor, "unsubscribeUrl", unsubscribeUrl),
                attachments,
                null
        );
    }

    private MailAttachment loadAttachment(String filename) {
        Resource resource = new ClassPathResource("mail-attachments/" + filename);
        try (InputStream in = resource.getInputStream()) {
            byte[] content = StreamUtils.copyToByteArray(in);
            String contentType = filename.endsWith(".pdf") ? "application/pdf" : "application/octet-stream";
            return new MailAttachment(filename, content, contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Attachment nicht lesbar: " + filename, e);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
