package org.example.scheduler.repository;

import org.example.scheduler.domain.AvailabilityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, Long> {
}
