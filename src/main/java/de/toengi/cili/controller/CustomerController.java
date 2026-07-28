package de.toengi.cili.controller;

import de.toengi.cili.dto.customer.CreateCustomerRequest;
import de.toengi.cili.dto.customer.CustomerDto;
import de.toengi.cili.model.enums.UserRole;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<CustomerDto> create(@Valid @RequestBody CreateCustomerRequest request,
                                               @AuthenticationPrincipal CiliUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(customerService.createCustomer(user.getUserId(), request));
    }

    @GetMapping
    public ResponseEntity<List<CustomerDto>> list(@AuthenticationPrincipal CiliUserDetails user) {
        boolean isAdmin = user.getRole() == UserRole.ADMIN;
        return ResponseEntity.ok(customerService.listOwnCustomers(user.getUserId(), isAdmin));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerDto> get(@PathVariable Long id,
                                            @AuthenticationPrincipal CiliUserDetails user) {
        boolean isAdmin = user.getRole() == UserRole.ADMIN;
        return ResponseEntity.ok(customerService.getCustomer(id, user.getUserId(), isAdmin));
    }
}
