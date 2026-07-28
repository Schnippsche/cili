package de.toengi.cili.service;

import de.toengi.cili.dto.customer.CreateCustomerRequest;
import de.toengi.cili.dto.customer.CustomerDto;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Customer;
import de.toengi.cili.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository);
    }

    @Test
    void createCustomer_savesWithConsentGrantedImmediately() {
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerDto dto = customerService.createCustomer(10L,
                new CreateCustomerRequest("Anna Beispiel", null, "anna@example.com", null, null, null, null, null));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        Customer saved = captor.getValue();
        assertThat(saved.isConsentGranted()).isTrue();
        assertThat(saved.getConsentGrantedAt()).isNotNull();
        assertThat(saved.getUnsubscribeToken()).isNotBlank();
        assertThat(saved.getSponsorUserId()).isEqualTo(10L);
        assertThat(dto.email()).isEqualTo("anna@example.com");
    }

    @Test
    void createCustomer_whenDuplicateEmailForSponsor_throwsConflict() {
        // Simuliert die DataIntegrityViolationException, die H2/MySQL bei einem echten
        // Unique-Constraint-Verstoss wirft. Ob der Constraint (sponsor_user_id, email) auf
        // Entity-Ebene tatsaechlich korrekt deklariert ist, prueft dieser Mockito-Test NICHT —
        // das verifiziert erst CustomerIntegrationTest.createCustomer_duplicateEmailSameSponsor_returns409
        // in Task 4 gegen die echte H2-Testdatenbank.
        when(customerRepository.save(any(Customer.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> customerService.createCustomer(10L,
                new CreateCustomerRequest("Anna", null, "anna@example.com", null, null, null, null, null)))
                .isInstanceOf(CiliException.class)
                .satisfies(e -> assertThat(((CiliException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void listOwnCustomers_nonAdmin_filtersBySponsor() {
        when(customerRepository.findBySponsorUserId(10L)).thenReturn(List.of(customer(1L, 10L)));

        List<CustomerDto> result = customerService.listOwnCustomers(10L, false);

        assertThat(result).hasSize(1);
        verify(customerRepository, never()).findAll();
    }

    @Test
    void listOwnCustomers_admin_seesAll() {
        when(customerRepository.findAll()).thenReturn(List.of(customer(1L, 10L), customer(2L, 20L)));

        List<CustomerDto> result = customerService.listOwnCustomers(99L, true);

        assertThat(result).hasSize(2);
    }

    @Test
    void getCustomer_ownSponsor_returnsCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, 10L)));

        CustomerDto dto = customerService.getCustomer(1L, 10L, false);

        assertThat(dto.id()).isEqualTo(1L);
    }

    @Test
    void getCustomer_foreignSponsor_throwsForbidden() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, 10L)));

        assertThatThrownBy(() -> customerService.getCustomer(1L, 20L, false))
                .isInstanceOf(CiliException.class)
                .satisfies(e -> assertThat(((CiliException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void getCustomer_admin_canAccessForeignCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, 10L)));

        CustomerDto dto = customerService.getCustomer(1L, 99L, true);

        assertThat(dto.id()).isEqualTo(1L);
    }

    @Test
    void unsubscribe_validToken_revokesConsent() {
        Customer c = customer(1L, 10L);
        when(customerRepository.findByUnsubscribeToken("tok-1")).thenReturn(Optional.of(c));

        customerService.unsubscribe("tok-1");

        assertThat(c.isConsentGranted()).isFalse();
        assertThat(c.getConsentRevokedAt()).isNotNull();
        verify(customerRepository).save(c);
    }

    @Test
    void unsubscribe_calledTwice_isIdempotent() {
        Customer c = customer(1L, 10L);
        c.setConsentGranted(false);
        c.setConsentRevokedAt(LocalDateTime.now().minusDays(1));
        when(customerRepository.findByUnsubscribeToken("tok-1")).thenReturn(Optional.of(c));

        customerService.unsubscribe("tok-1");

        verify(customerRepository, never()).save(any());
    }

    @Test
    void unsubscribe_unknownToken_throwsNotFound() {
        when(customerRepository.findByUnsubscribeToken("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.unsubscribe("unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verifyToken_unknownToken_throwsNotFound() {
        when(customerRepository.findByUnsubscribeToken("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.verifyToken("unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Customer customer(Long id, Long sponsorUserId) {
        return Customer.builder()
                .id(id).name("Kunde " + id).email("kunde" + id + "@example.com")
                .sponsorUserId(sponsorUserId).consentGranted(true)
                .consentGrantedAt(LocalDateTime.now()).unsubscribeToken("tok-" + id)
                .build();
    }
}
