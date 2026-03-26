package org.example.scheduler.repository;

import org.example.scheduler.domain.UnavailableRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnavailableRangeRepository extends JpaRepository<UnavailableRange, Long> {
}
