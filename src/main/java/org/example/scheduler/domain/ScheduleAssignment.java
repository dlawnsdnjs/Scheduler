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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private TaskDefinition task;

    @Column(nullable = false)
    private LocalDate assignedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status = AssignmentStatus.AUTOMATIC;

    private String note;

    public ScheduleAssignment(TaskDefinition task, LocalDate assignedDate, Participant participant) {
        this.task = task;
        this.assignedDate = assignedDate;
        this.participant = participant;
    }

    public Long getTaskId() { return task.getId(); }
    public Long getParticipantId() { return participant.getId(); }
}
