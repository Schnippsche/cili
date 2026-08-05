package de.toengi.cili.repository;

import de.toengi.cili.model.entity.MailflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MailflowInstanceRepository extends JpaRepository<MailflowInstance, Long> {

    List<MailflowInstance> findByCustomerId(Long customerId);

    List<MailflowInstance> findByCustomerIdAndFlowName(Long customerId, String flowName);
}
