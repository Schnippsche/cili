package de.toengi.cili.service;

import de.toengi.cili.config.MailConfig;
import de.toengi.cili.config.MailflowConfig;
import de.toengi.cili.model.entity.Customer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MailflowStepProcessorTest {

    @Mock de.toengi.cili.repository.MailflowStepStatusRepository stepRepository;
    @Mock de.toengi.cili.repository.MailflowInstanceRepository instanceRepository;
    @Mock de.toengi.cili.repository.CustomerRepository customerRepository;
    @Mock de.toengi.cili.repository.UserRepository userRepository;
    @Mock MailflowConfig config;
    @Mock MailConfig mailConfig;
    @Mock MailService mailService;
    @Mock PlatformTransactionManager transactionManager;

    private final MailflowStepProcessor processor = new MailflowStepProcessor(
            stepRepository, instanceRepository, customerRepository, userRepository,
            config, mailConfig, mailService, transactionManager);

    private MailflowConfig.StepDefinition stepWithTemplates(String sie, String du) {
        MailflowConfig.StepDefinition step = new MailflowConfig.StepDefinition();
        step.setTemplate(sie);
        step.setTemplateInformal(du);
        return step;
    }

    private Customer customer(Boolean informalAddress) {
        return Customer.builder().informalAddress(informalAddress).build();
    }

    @Test
    void resolveTemplate_informalCustomerWithDuTemplate_usesDuTemplate() {
        var step = stepWithTemplates("welcome", "welcome-du");
        assertThat(processor.resolveTemplate(customer(true), step)).isEqualTo("welcome-du");
    }

    @Test
    void resolveTemplate_formalCustomer_usesSieTemplate() {
        var step = stepWithTemplates("welcome", "welcome-du");
        assertThat(processor.resolveTemplate(customer(false), step)).isEqualTo("welcome");
    }

    @Test
    void resolveTemplate_informalCustomerWithoutDuTemplate_fallsBackToSieTemplate() {
        var step = stepWithTemplates("welcome", null);
        assertThat(processor.resolveTemplate(customer(true), step)).isEqualTo("welcome");
    }

    @Test
    void resolveTemplate_nullInformalAddress_usesSieTemplate() {
        var step = stepWithTemplates("welcome", "welcome-du");
        assertThat(processor.resolveTemplate(customer(null), step)).isEqualTo("welcome");
    }
}
