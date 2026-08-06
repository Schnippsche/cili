package de.toengi.cili.controller;

import de.toengi.cili.dto.mailflow.AvailableMailflowDto;
import de.toengi.cili.dto.mailflow.MailflowInstanceDto;
import de.toengi.cili.dto.mailflow.StartMailflowRequest;
import de.toengi.cili.model.enums.UserRole;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.MailflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MailflowController {

    private final MailflowService mailflowService;

    @GetMapping("/api/mailflows")
    public ResponseEntity<List<AvailableMailflowDto>> listAvailable() {
        return ResponseEntity.ok(mailflowService.listAvailableFlows());
    }

    @PostMapping("/api/customers/{customerId}/mailflows")
    public ResponseEntity<MailflowInstanceDto> start(@PathVariable Long customerId,
                                                       @Valid @RequestBody StartMailflowRequest request,
                                                       @AuthenticationPrincipal CiliUserDetails user) {
        boolean isAdmin = user.getRole() == UserRole.ADMIN;
        MailflowInstanceDto dto =
                mailflowService.startFlow(customerId, request.flowName(), user.getUserId(), isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/api/customers/{customerId}/mailflows")
    public ResponseEntity<List<MailflowInstanceDto>> list(@PathVariable Long customerId,
                                                            @AuthenticationPrincipal CiliUserDetails user) {
        boolean isAdmin = user.getRole() == UserRole.ADMIN;
        return ResponseEntity.ok(mailflowService.listInstances(customerId, user.getUserId(), isAdmin));
    }

    @DeleteMapping("/api/customers/{customerId}/mailflows/{instanceId}")
    public ResponseEntity<Void> delete(@PathVariable Long customerId,
                                        @PathVariable Long instanceId,
                                        @AuthenticationPrincipal CiliUserDetails user) {
        boolean isAdmin = user.getRole() == UserRole.ADMIN;
        mailflowService.deleteInstance(customerId, instanceId, user.getUserId(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/customers/{customerId}/mailflows/{instanceId}/steps/{stepId}/send-now")
    public ResponseEntity<MailflowInstanceDto> sendStepNow(@PathVariable Long customerId,
                                                             @PathVariable Long instanceId,
                                                             @PathVariable String stepId,
                                                             @AuthenticationPrincipal CiliUserDetails user) {
        boolean isAdmin = user.getRole() == UserRole.ADMIN;
        return ResponseEntity.ok(mailflowService.sendStepNow(customerId, instanceId, stepId, isAdmin));
    }
}
