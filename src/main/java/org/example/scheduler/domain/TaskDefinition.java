package org.example.scheduler.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "task_definitions")
@Getter
@Setter
@NoArgsConstructor
public class TaskDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String taskName;

    @Column(nullable = false)
    private String cycleType; // WEEKLY, INTERVAL, etc.

    @Column(nullable = false)
    private String cycleValue; // JSON representation of rules or comma-separated days

    @Column(nullable = false)
    private int requiredParticipantsPerDay = 1;

    @Column
    private String color; // HEX 색상 코드 (예: #ff0000)

    @ManyToMany
    @JoinTable(
        name = "task_participants",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "participant_id")
    )
    private List<Participant> allowedParticipants = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "task_conflicts",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "conflict_task_id")
    )
    private List<TaskDefinition> conflictingTasks = new ArrayList<>();

    public TaskDefinition(String taskName, String cycleType, String cycleValue, int requiredParticipantsPerDay) {
        this.taskName = taskName;
        this.cycleType = cycleType;
        this.cycleValue = cycleValue;
        this.requiredParticipantsPerDay = requiredParticipantsPerDay;
    }
}
