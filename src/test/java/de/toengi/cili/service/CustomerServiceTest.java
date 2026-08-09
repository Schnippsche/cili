package de.toengi.cili.service;

import de.toengi.cili.dto.customer.CreateCustomerRequest;
import de.toengi.cili.dto.customer.CustomerDto;
import de.toengi.cili.dto.customer.UpdateCustomerRequest;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Customer;
import de.toengi.cili.repository.CustomerRepository;
import de.toengi.cili.repository.MailflowInstanceRepository;
import de.toengi.cili.repository.MailflowStepStatusRepository;
import de.toengi.cili.repository.UserRepository;
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
    @Mock private MailflowInstanceRepository mailflowInstanceRepository;
    @Mock private MailflowStepStatusRepository mailflowStepStatusRepository;
    @Mock private UserRepository userRepository;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(
                customerRepository, mailflowInstanceRepository, mailflowStepStatusRepository, userRepository);
    }

    @Test
    void createCustomer_savesWithConsentGrantedImmediately() {
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerDto dto = customerService.createCustomer(10L, false,
                new CreateCustomerRequest("Anna Beispiel", null, "anna@example.com", null, null, null, null, null, null));

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

        assertThatThrownBy(() -> customerService.createCustomer(10L, false,
                new CreateCustomerRequest("Anna", null, "anna@example.com", null, null, null, null, null, null)))
                .isInstanceOf(CiliException.class)
                .satisfies(e -> assertThat(((CiliException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void createCustomer_adminWithSponsorUserId_assignsToChosenUser() {
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.existsById(42L)).thenReturn(true);

        customerService.createCustomer(99L, true,
                new CreateCustomerRequest("Anna", null, "anna@example.com", null, null, null, null, null, 42L));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getSponsorUserId()).isEqualTo(42L);
    }

    @Test
    void createCustomer_adminWithUnknownSponsorUserId_throwsNotFound() {
        when(userRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> customerService.createCustomer(99L, true,
                new CreateCustomerRequest("Anna", null, "anna@example.com", null, null, null, null, null, 404L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void createCustomer_nonAdminWithSponsorUserId_isIgnored() {
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        customerService.createCustomer(10L, false,
                new CreateCustomerRequest("Anna", null, "anna@example.com", null, null, null, null, null, 42L));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getSponsorUserId()).isEqualTo(10L);
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

    @Test
    void updateCustomer_adminWithSponsorUserId_reassignsSponsor() {
        Customer c = customer(1L, 10L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.existsById(42L)).thenReturn(true);

        customerService.updateCustomer(1L, 99L, true,
                new UpdateCustomerRequest(null, null, null, null, null, null, null, null, 42L));

        assertThat(c.getSponsorUserId()).isEqualTo(42L);
    }

    @Test
    void updateCustomer_adminWithUnknownSponsorUserId_throwsNotFound() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, 10L)));
        when(userRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> customerService.updateCustomer(1L, 99L, true,
                new UpdateCustomerRequest(null, null, null, null, null, null, null, null, 404L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void updateCustomer_nonAdminWithSponsorUserId_isIgnored() {
        Customer c = customer(1L, 10L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        customerService.updateCustomer(1L, 10L, false,
                new UpdateCustomerRequest(null, null, null, null, null, null, null, null, 42L));

        assertThat(c.getSponsorUserId()).isEqualTo(10L);
    }

    @Test
    void deleteCustomer_ownSponsor_deletesCascadingMailflowHistory() {
        Customer c = customer(1L, 10L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));
        var instance = de.toengi.cili.model.entity.MailflowInstance.builder().id(5L).customerId(1L).build();
        when(mailflowInstanceRepository.findByCustomerId(1L)).thenReturn(List.of(instance));
        var step = de.toengi.cili.model.entity.MailflowStepStatus.builder().id(50L).instanceId(5L).build();
        when(mailflowStepStatusRepository.findByInstanceId(5L)).thenReturn(List.of(step));

        customerService.deleteCustomer(1L, 10L, false);

        verify(mailflowStepStatusRepository).deleteAll(List.of(step));
        verify(mailflowInstanceRepository).deleteAllById(List.of(5L));
        verify(customerRepository).delete(c);
    }

    @Test
    void deleteCustomer_foreignSponsor_throwsForbidden() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, 10L)));

        assertThatThrownBy(() -> customerService.deleteCustomer(1L, 20L, false))
                .isInstanceOf(CiliException.class)
                .satisfies(e -> assertThat(((CiliException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(customerRepository, never()).delete(any());
    }

    @Test
    void deleteCustomer_admin_canDeleteForeignCustomer() {
        Customer c = customer(1L, 10L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));
        when(mailflowInstanceRepository.findByCustomerId(1L)).thenReturn(List.of());

        customerService.deleteCustomer(1L, 99L, true);

        verify(customerRepository).delete(c);
    }

    private Customer customer(Long id, Long sponsorUserId) {
        return Customer.builder()
                .id(id).name("Kunde " + id).email("kunde" + id + "@example.com")
                .sponsorUserId(sponsorUserId).consentGranted(true)
                .consentGrantedAt(LocalDateTime.now()).unsubscribeToken("tok-" + id)
                .build();
    }
}
