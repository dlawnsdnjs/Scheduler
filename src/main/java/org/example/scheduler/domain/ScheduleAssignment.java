package org.example.scheduler.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "schedule_assignments")
@Getter
@Setter
@NoArgsConstructor
public class ScheduleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private LocalDate assignedDate;

    @Column(nullable = false)
    private Long participantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status = AssignmentStatus.AUTOMATIC;

    private String note;

    public ScheduleAssignment(Long taskId, LocalDate assignedDate, Long participantId) {
        this.taskId = taskId;
        this.assignedDate = assignedDate;
        this.participantId = participantId;
    }
}
