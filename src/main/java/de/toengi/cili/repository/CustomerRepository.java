package de.toengi.cili.repository;

import de.toengi.cili.model.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findBySponsorUserId(Long sponsorUserId);

    Optional<Customer> findByUnsubscribeToken(String unsubscribeToken);
}
