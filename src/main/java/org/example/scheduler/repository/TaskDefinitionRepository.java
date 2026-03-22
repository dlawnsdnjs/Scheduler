package org.example.scheduler.repository;

import org.example.scheduler.domain.TaskDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskDefinitionRepository extends JpaRepository<TaskDefinition, Long> {
}
