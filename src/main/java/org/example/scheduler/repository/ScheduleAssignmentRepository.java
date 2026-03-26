package org.example.scheduler.repository;

import org.example.scheduler.domain.AssignmentStatus;
import org.example.scheduler.domain.ScheduleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScheduleAssignmentRepository extends JpaRepository<ScheduleAssignment, Long> {
    List<ScheduleAssignment> findByTaskId(Long taskId);
    List<ScheduleAssignment> findByTaskIdAndAssignedDateBefore(Long taskId, LocalDate date);
    List<ScheduleAssignment> findByTaskIdAndAssignedDateBetweenAndStatus(Long taskId, LocalDate start, LocalDate end, AssignmentStatus status);
    List<ScheduleAssignment> findByTaskIdAndAssignedDateBetween(Long taskId, LocalDate start, LocalDate end);
    List<ScheduleAssignment> findByAssignedDateBetween(LocalDate start, LocalDate end);
    void deleteByTaskIdAndAssignedDateBetweenAndStatus(Long taskId, LocalDate start, LocalDate end, org.example.scheduler.domain.AssignmentStatus status);
}
