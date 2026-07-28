package de.toengi.cili.service;

import de.toengi.cili.dto.customer.CreateCustomerRequest;
import de.toengi.cili.dto.customer.CustomerDto;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Customer;
import de.toengi.cili.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional
    public CustomerDto createCustomer(Long sponsorUserId, CreateCustomerRequest request) {
        try {
            Customer customer = Customer.builder()
                    .name(request.name().trim())
                    .firstName(request.firstName())
                    .email(request.email().trim())
                    .mobilePhone(request.mobilePhone())
                    .birthDate(request.birthDate())
                    .memberId(request.memberId())
                    .gender(request.gender())
                    .informalAddress(request.informalAddress())
                    .sponsorUserId(sponsorUserId)
                    .consentGranted(true)
                    .consentGrantedAt(LocalDateTime.now())
                    .unsubscribeToken(UUID.randomUUID().toString())
                    .build();
            return toDto(customerRepository.save(customer));
        } catch (DataIntegrityViolationException e) {
            throw new CiliException("Kunde mit dieser E-Mail existiert bereits.", HttpStatus.CONFLICT);
        }
    }

    @Transactional(readOnly = true)
    public List<CustomerDto> listOwnCustomers(Long currentUserId, boolean isAdmin) {
        List<Customer> customers = isAdmin
                ? customerRepository.findAll()
                : customerRepository.findBySponsorUserId(currentUserId);
        return customers.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CustomerDto getCustomer(Long id, Long currentUserId, boolean isAdmin) {
        Customer customer = findOrThrow(id);
        checkOwnerOrAdmin(customer, currentUserId, isAdmin);
        return toDto(customer);
    }

    private Customer findOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private void checkOwnerOrAdmin(Customer customer, Long currentUserId, boolean isAdmin) {
        if (!customer.getSponsorUserId().equals(currentUserId) && !isAdmin) {
            throw new CiliException("Zugriff verweigert", HttpStatus.FORBIDDEN);
        }
    }

    @Transactional
    public void unsubscribe(String token) {
        Customer customer = customerRepository.findByUnsubscribeToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", 0L));
        if (customer.isConsentGranted()) {
            customer.setConsentGranted(false);
            customer.setConsentRevokedAt(LocalDateTime.now());
            customerRepository.save(customer);
        }
        // Bereits abgemeldete Kunden: kein erneutes save() noetig (Idempotenz).
    }

    @Transactional(readOnly = true)
    public void verifyToken(String token) {
        customerRepository.findByUnsubscribeToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", 0L));
    }

    private CustomerDto toDto(Customer c) {
        return new CustomerDto(c.getId(), c.getName(), c.getFirstName(), c.getEmail(), c.getMobilePhone(),
                c.getBirthDate(), c.getMemberId(), c.getGender(), c.getInformalAddress(), c.getSponsorUserId(),
                c.isConsentGranted(), c.getConsentGrantedAt(), c.getConsentRevokedAt(), c.getCreatedAt());
    }
}
