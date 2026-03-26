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
        // 모든 순환 규칙을 기준일(2026-01-01) 기반 2일 주기 계산으로 통일
        LocalDate baseDate = LocalDate.of(2026, 1, 1);
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(baseDate, date);
        int remainder = (int) Math.floorMod(daysBetween, 2); // 0 또는 1

        switch (rule.getRuleType()) {
            case "EVEN_DAYS": // 짝수일 배정 (나머지 0인 날)
                return remainder == 0;
            case "ODD_DAYS": // 홀수일 배정 (나머지 1인 날)
                return remainder == 1;
            case "WEEKDAYS_ONLY":
                return date.getDayOfWeek().getValue() <= 5;
            case "WEEKENDS_ONLY":
                return date.getDayOfWeek().getValue() >= 6;
            case "N_DAY_CYCLE":
                if (rule.getRuleValue() == null || !rule.getRuleValue().contains(":")) return false;
                try {
                    String[] parts = rule.getRuleValue().split(":");
                    LocalDate base = LocalDate.parse(parts[0]);
                    int cycle = Integer.parseInt(parts[1]);
                    long diff = java.time.temporal.ChronoUnit.DAYS.between(base, date);
                    return Math.floorMod(diff, cycle) == 0;
                } catch (Exception e) {
                    return false;
                }
            default:
                return true;
        }
    }

    public void addAvailabilityRule(String ruleType, String ruleValue) {
        AvailabilityRule rule = new AvailabilityRule(this, ruleType);
        rule.setRuleValue(ruleValue);
        this.availabilityRules.add(rule);
    }
}
