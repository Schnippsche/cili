package de.toengi.cili.service;

import de.toengi.cili.dto.customer.CreateCustomerRequest;
import de.toengi.cili.dto.customer.CustomerDto;
import de.toengi.cili.dto.customer.UpdateCustomerRequest;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Customer;
import de.toengi.cili.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
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

    @Transactional
    public CustomerDto updateCustomer(Long id, Long currentUserId, boolean isAdmin,
                                       UpdateCustomerRequest request) {
        Customer customer = findOrThrow(id);
        checkOwnerOrAdmin(customer, currentUserId, isAdmin);

        try {
            if (StringUtils.hasText(request.name())) {
                customer.setName(request.name().trim());
            }
            if (request.firstName() != null) {
                customer.setFirstName(request.firstName());
            }
            if (StringUtils.hasText(request.email())) {
                customer.setEmail(request.email().trim());
            }
            if (request.mobilePhone() != null) {
                customer.setMobilePhone(request.mobilePhone());
            }
            if (request.birthDate() != null) {
                customer.setBirthDate(request.birthDate());
            }
            if (request.memberId() != null) {
                customer.setMemberId(request.memberId());
            }
            if (request.gender() != null) {
                customer.setGender(request.gender());
            }
            if (request.informalAddress() != null) {
                customer.setInformalAddress(request.informalAddress());
            }
            return toDto(customerRepository.save(customer));
        } catch (DataIntegrityViolationException e) {
            throw new CiliException("Kunde mit dieser E-Mail existiert bereits.", HttpStatus.CONFLICT);
        }
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
            log.info("Kunde abgemeldet: id={} email={}", customer.getId(), customer.getEmail());
        } else {
            // Bereits abgemeldete Kunden: kein erneutes save() noetig (Idempotenz).
            log.debug("Abmeldelink erneut aufgerufen (bereits abgemeldet): id={} email={}",
                    customer.getId(), customer.getEmail());
        }
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
