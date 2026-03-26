package org.example.scheduler.repository;

import org.example.scheduler.domain.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    List<Participant> findByLeaveDateIsNull();
    Optional<Participant> findByName(String name);
}
