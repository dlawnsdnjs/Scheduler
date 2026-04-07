package org.example.scheduler.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * 작업 정의에 따른 배정 대상 날짜 리스트를 생성합니다.
     */
    public List<LocalDate> getTargetDates(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = start;

        if ("WEEKLY".equals(this.cycleType)) {
            Set<DayOfWeek> targetDays = Arrays.stream(this.cycleValue.split(","))
                    .map(String::trim)
                    .map(this::parseKoreanDay)
                    .collect(Collectors.toSet());

            while (!current.isAfter(end)) {
                if (targetDays.contains(current.getDayOfWeek())) {
                    dates.add(current);
                }
                current = current.plusDays(1);
            }
        } else if ("INTERVAL".equals(this.cycleType)) {
            int interval = Integer.parseInt(this.cycleValue);
            while (!current.isAfter(end)) {
                dates.add(current);
                current = current.plusDays(interval);
            }
        }
        return dates;
    }

    private DayOfWeek parseKoreanDay(String day) {
        return switch (day.toUpperCase()) {
            case "월", "MON" -> DayOfWeek.MONDAY;
            case "화", "TUE" -> DayOfWeek.TUESDAY;
            case "수", "WED" -> DayOfWeek.WEDNESDAY;
            case "목", "THU" -> DayOfWeek.THURSDAY;
            case "금", "FRI" -> DayOfWeek.FRIDAY;
            case "토", "SAT" -> DayOfWeek.SATURDAY;
            case "일", "SUN" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("지원하지 않는 요일 형식: " + day);
        };
    }

    public void addConflict(TaskDefinition other) {
        if (!this.conflictingTasks.contains(other)) {
            this.conflictingTasks.add(other);
            other.getConflictingTasks().add(this);
        }
    }
}
