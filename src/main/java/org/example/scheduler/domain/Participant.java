package org.example.scheduler.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "participants")
@Getter
@Setter
@NoArgsConstructor
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate joinDate;

    private LocalDate leaveDate;

    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UnavailableRange> unavailableRanges = new ArrayList<>();

    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AvailabilityRule> availabilityRules = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "participant_task_counts", joinColumns = @JoinColumn(name = "participant_id"))
    @MapKeyColumn(name = "task_id")
    @Column(name = "total_count")
    private Map<Long, Integer> taskTotalCounts = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "participant_task_last_dates", joinColumns = @JoinColumn(name = "participant_id"))
    @MapKeyColumn(name = "task_id")
    @Column(name = "last_date")
    private Map<Long, LocalDate> taskLastAssignedDates = new HashMap<>();

    public Participant(String name, LocalDate joinDate) {
        this.name = name;
        this.joinDate = joinDate;
    }

    public void addUnavailableRange(LocalDate start, LocalDate end) {
        UnavailableRange range = new UnavailableRange(this, start, end);
        this.unavailableRanges.add(range);
    }

    public int getTaskCount(Long taskId) {
        return taskTotalCounts.getOrDefault(taskId, 0);
    }

    public LocalDate getLastDate(Long taskId) {
        return taskLastAssignedDates.getOrDefault(taskId, LocalDate.MIN);
    }

    public void incrementTaskCount(Long taskId, LocalDate date) {
        taskTotalCounts.put(taskId, getTaskCount(taskId) + 1);
        taskLastAssignedDates.put(taskId, date);
    }

    public boolean isAvailable(LocalDate date) {
        // 1. 입사/퇴사 기간 체크
        if (date.isBefore(joinDate)) return false;
        if (leaveDate != null && date.isAfter(leaveDate)) return false;

        // 2. 불참 기간(Range)에 포함되면 불가
        for (UnavailableRange range : unavailableRanges) {
            if (!date.isBefore(range.getStartDate()) && !date.isAfter(range.getEndDate())) {
                return false;
            }
        }

        // 3. 가용 규칙(AvailabilityRule) 체크
        if (!availabilityRules.isEmpty()) {
            boolean matchesAtLeastOne = false;
            for (AvailabilityRule rule : availabilityRules) {
                if (checkRule(rule, date)) {
                    matchesAtLeastOne = true;
                    break;
                }
            }
            // 규칙이 등록되어 있다면 적어도 하나의 규칙은 만족해야 배정 가능
            if (!matchesAtLeastOne) return false;
        }

        return true;
    }

    private boolean checkRule(AvailabilityRule rule, LocalDate date) {
        int dayOfMonth = date.getDayOfMonth();
        switch (rule.getRuleType()) {
            case "EVEN_DAYS":
                return dayOfMonth % 2 == 0;
            case "ODD_DAYS":
                return dayOfMonth % 2 != 0;
            case "WEEKDAYS_ONLY":
                return date.getDayOfWeek().getValue() <= 5;
            case "WEEKENDS_ONLY":
                return date.getDayOfWeek().getValue() >= 6;
            // 필요 시 추가 패턴 구현 (예: N-Day Cycle)
            default:
                return true;
        }
    }

    public void addAvailabilityRule(String ruleType) {
        this.availabilityRules.add(new AvailabilityRule(this, ruleType));
    }
}
