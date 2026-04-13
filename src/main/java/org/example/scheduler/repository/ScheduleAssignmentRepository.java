package org.example.scheduler.repository;

import org.example.scheduler.domain.AssignmentStatus;
import org.example.scheduler.domain.Participant;
import org.example.scheduler.domain.ScheduleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScheduleAssignmentRepository extends JpaRepository<ScheduleAssignment, Long> {
    @Query("SELECT sa FROM ScheduleAssignment sa WHERE sa.participant.id = :participantId AND sa.task.id = :taskId")
    List<ScheduleAssignment> findByParticipantIdAndTaskId(@Param("participantId") Long participantId, @Param("taskId") Long taskId);

    @Query("SELECT sa FROM ScheduleAssignment sa WHERE sa.task.id = :taskId")
    List<ScheduleAssignment> findByTaskId(@Param("taskId") Long taskId);

    @Query("SELECT sa FROM ScheduleAssignment sa WHERE sa.task.id = :taskId AND sa.assignedDate < :date")
    List<ScheduleAssignment> findByTaskIdAndAssignedDateBefore(@Param("taskId") Long taskId, @Param("date") LocalDate date);
    
    @Query("SELECT sa FROM ScheduleAssignment sa WHERE sa.task.id IN :taskIds AND sa.assignedDate BETWEEN :start AND :end AND sa.status = :status")
    List<ScheduleAssignment> findByTaskIdInAndAssignedDateBetweenAndStatus(@Param("taskIds") List<Long> taskIds, @Param("start") LocalDate start, @Param("end") LocalDate end, @Param("status") AssignmentStatus status);
    
    @Query("SELECT sa FROM ScheduleAssignment sa WHERE sa.task.id IN :taskIds")
    List<ScheduleAssignment> findByTaskIdIn(@Param("taskIds") List<Long> taskIds);
    
    @Query("SELECT sa FROM ScheduleAssignment sa WHERE sa.task.id = :taskId AND sa.assignedDate BETWEEN :start AND :end AND sa.status = :status")
    List<ScheduleAssignment> findByTaskIdAndAssignedDateBetweenAndStatus(@Param("taskId") Long taskId, @Param("start") LocalDate start, @Param("end") LocalDate end, @Param("status") AssignmentStatus status);

    @Query("SELECT sa FROM ScheduleAssignment sa WHERE sa.task.id = :taskId AND sa.assignedDate BETWEEN :start AND :end")
    List<ScheduleAssignment> findByTaskIdAndAssignedDateBetween(@Param("taskId") Long taskId, @Param("start") LocalDate start, @Param("end") LocalDate end);
    
    @Query("SELECT sa FROM ScheduleAssignment sa WHERE sa.assignedDate BETWEEN :start AND :end")
    List<ScheduleAssignment> findByAssignedDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT sa FROM ScheduleAssignment sa WHERE sa.task.id IN :taskIds AND sa.assignedDate IN :dates AND sa.participant IN :participants")
    List<ScheduleAssignment> findByTaskIdInAndAssignedDateInAndParticipantIdIn(@Param("taskIds") List<Long> taskIds, @Param("dates") List<LocalDate> dates, @Param("participants") List<Participant> participants);

    @Query("DELETE FROM ScheduleAssignment sa WHERE sa.task.id = :taskId AND sa.assignedDate BETWEEN :start AND :end AND sa.status = :status")
    void deleteByTaskIdAndAssignedDateBetweenAndStatus(@Param("taskId") Long taskId, @Param("start") LocalDate start, @Param("end") LocalDate end, @Param("status") AssignmentStatus status);

    @Query("SELECT sa FROM ScheduleAssignment sa JOIN FETCH sa.task JOIN FETCH sa.participant WHERE sa.assignedDate BETWEEN :start AND :end")
    List<ScheduleAssignment> findAllWithTaskAndParticipant(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
