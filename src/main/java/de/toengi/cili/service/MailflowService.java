package de.toengi.cili.service;

import de.toengi.cili.config.MailflowConfig;
import de.toengi.cili.dto.mailflow.AvailableMailflowDto;
import de.toengi.cili.dto.mailflow.MailflowInstanceDto;
import de.toengi.cili.dto.mailflow.MailflowStepDto;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Customer;
import de.toengi.cili.model.entity.MailflowInstance;
import de.toengi.cili.model.entity.MailflowStepStatus;
import de.toengi.cili.model.enums.MailflowStepState;
import de.toengi.cili.repository.CustomerRepository;
import de.toengi.cili.repository.MailflowInstanceRepository;
import de.toengi.cili.repository.MailflowStepStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MailflowService {

    private final MailflowInstanceRepository instanceRepository;
    private final MailflowStepStatusRepository stepRepository;
    private final CustomerRepository customerRepository;
    private final MailflowConfig config;

    public List<AvailableMailflowDto> listAvailableFlows() {
        return config.getFlows().entrySet().stream()
                .map(e -> new AvailableMailflowDto(e.getKey(), e.getValue().getDescription()))
                .toList();
    }

    @Transactional
    public MailflowInstanceDto startFlow(Long customerId, String flowName, Long currentUserId, boolean isAdmin) {
        Customer customer = findCustomerOrThrow(customerId);
        checkOwnerOrAdmin(customer, currentUserId, isAdmin);

        MailflowConfig.FlowDefinition flow = config.findFlow(flowName)
                .orElseThrow(() -> new ResourceNotFoundException("Mailflow", flowName));

        boolean hasOpenInstance = instanceRepository.findByCustomerIdAndFlowName(customerId, flowName).stream()
                .anyMatch(this::isOpen);
        if (hasOpenInstance) {
            throw new CiliException(
                    "Für diesen Kunden läuft bereits eine aktive Instanz von '" + flowName + "'",
                    HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        MailflowInstance instance = instanceRepository.save(MailflowInstance.builder()
                .customerId(customerId)
                .flowName(flowName)
                .startedAt(now)
                .createdByUserId(currentUserId)
                .build());

        LocalDate startDate = now.toLocalDate();
        List<MailflowStepStatus> steps = flow.getSteps().stream()
                .map(stepDef -> MailflowStepStatus.builder()
                        .instanceId(instance.getId())
                        .stepId(stepDef.getId())
                        .scheduledFor(startDate.plusDays(stepDef.getDelayDays()))
                        .status(MailflowStepState.PENDING)
                        .build())
                .toList();
        stepRepository.saveAll(steps);

        return toDto(instance, flow.getDescription(), steps);
    }

    @Transactional(readOnly = true)
    public List<MailflowInstanceDto> listInstances(Long customerId, Long currentUserId, boolean isAdmin) {
        Customer customer = findCustomerOrThrow(customerId);
        checkOwnerOrAdmin(customer, currentUserId, isAdmin);

        return instanceRepository.findByCustomerId(customerId).stream()
                .map(instance -> {
                    String description = config.findFlow(instance.getFlowName())
                            .map(MailflowConfig.FlowDefinition::getDescription)
                            .orElse(instance.getFlowName());
                    List<MailflowStepStatus> steps = stepRepository.findByInstanceId(instance.getId());
                    return toDto(instance, description, steps);
                })
                .toList();
    }

    private Customer findCustomerOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    }

    private void checkOwnerOrAdmin(Customer customer, Long currentUserId, boolean isAdmin) {
        if (!customer.getSponsorUserId().equals(currentUserId) && !isAdmin) {
            throw new CiliException("Zugriff verweigert", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isOpen(MailflowInstance instance) {
        return stepRepository.findByInstanceId(instance.getId()).stream()
                .anyMatch(s -> s.getStatus() == MailflowStepState.PENDING || s.getStatus() == MailflowStepState.ERROR);
    }

    private MailflowInstanceDto toDto(MailflowInstance instance, String description, List<MailflowStepStatus> steps) {
        boolean completed = steps.stream().allMatch(s ->
                s.getStatus() == MailflowStepState.SENT
                        || s.getStatus() == MailflowStepState.SKIPPED
                        || s.getStatus() == MailflowStepState.FAILED);
        List<MailflowStepDto> stepDtos = steps.stream()
                .map(s -> new MailflowStepDto(s.getStepId(), s.getScheduledFor(), s.getSentAt(),
                        s.getStatus().name(), s.getAttemptCount(), s.getLastError()))
                .toList();
        return new MailflowInstanceDto(instance.getId(), instance.getFlowName(), description,
                instance.getStartedAt(), completed ? "COMPLETED" : "RUNNING", stepDtos);
    }
}
