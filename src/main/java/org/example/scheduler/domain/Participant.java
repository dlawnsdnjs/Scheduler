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
        // 1. 참여 가능 기간 체크 (입사일 ~ 퇴사일)
        if (date.isBefore(joinDate)) return false;
        if (leaveDate != null && date.isAfter(leaveDate)) return false;

        // 2. 불참 기간(Range)에 포함되면 배정 불가 (우선순위 높음)
        for (UnavailableRange range : unavailableRanges) {
            if (!date.isBefore(range.getStartDate()) && !date.isAfter(range.getEndDate())) {
                return false;
            }
        }

        // 3. 가용성(참여 가능) 규칙 체크
        if (!availabilityRules.isEmpty()) {
            boolean matchesAtLeastOne = false;
            for (AvailabilityRule rule : availabilityRules) {
                if (checkRule(rule, date)) {
                    matchesAtLeastOne = true;
                    break;
                }
            }
            // 가용 규칙이 하나라도 등록되어 있다면, 적어도 하나는 만족해야 배정 가능
            if (!matchesAtLeastOne) return false;
        }

        return true;
    }

    private boolean checkRule(AvailabilityRule rule, LocalDate date) {
        try {
            switch (rule.getRuleType()) {
                case "EVEN_DAYS": // 기준일로부터 2일 주기 중 첫 번째 날(짝수번째 느낌)
                case "ODD_DAYS":  // 기준일로부터 2일 주기 중 두 번째 날(홀수번째 느낌)
                    if (rule.getRuleValue() == null) return true; // 기준일 없으면 기본 허용
                    LocalDate baseE = LocalDate.parse(rule.getRuleValue());
                    long diffE = java.time.temporal.ChronoUnit.DAYS.between(baseE, date);
                    int remainderE = (int) Math.floorMod(diffE, 2);
                    return "EVEN_DAYS".equals(rule.getRuleType()) ? (remainderE == 0) : (remainderE == 1);

                case "WEEKDAYS_ONLY":
                    return date.getDayOfWeek().getValue() <= 5;
                case "WEEKENDS_ONLY":
                    return date.getDayOfWeek().getValue() >= 6;

                case "N_DAY_CYCLE":
                    if (rule.getRuleValue() == null || !rule.getRuleValue().contains(":")) return false;
                    String[] parts = rule.getRuleValue().split(":");
                    LocalDate baseN = LocalDate.parse(parts[0]);
                    int cycle = Integer.parseInt(parts[1]);
                    long diffN = java.time.temporal.ChronoUnit.DAYS.between(baseN, date);
                    // N일 주기 중 첫 번째 날(나머지 0)에만 가용한 것으로 판단
                    return Math.floorMod(diffN, cycle) == 0;

                default:
                    return true;
            }
        } catch (Exception e) {
            return false; // 파싱 에러 시 안전하게 불가 처리
        }
    }

    public void addAvailabilityRule(String ruleType, String ruleValue) {
        AvailabilityRule rule = new AvailabilityRule(this, ruleType);
        rule.setRuleValue(ruleValue);
        this.availabilityRules.add(rule);
    }

    /**
     * 특정 작업 및 날짜에 대한 배정 적합도 점수를 계산합니다.
     * 점수가 높을수록 배정 우선순위가 높습니다.
     */
    public double calculateScore(Long taskId, LocalDate date, List<LocalDate> allOccurredDates, int totalParticipants, int maxTaskCount) {
        // 간격(G) 계산: 실제 업무 발생 횟수 기준
        LocalDate last = getLastDate(taskId);
        long gap = (last == LocalDate.MIN) ? totalParticipants : (allOccurredDates.stream().filter(d -> d.isAfter(last) && d.isBefore(date)).count() + 1);

        // 간격 점수: 전체 참여자 수(C)에 가까울수록 높은 점수
        double gapScore = totalParticipants - Math.abs(gap - totalParticipants);
        
        // 균등 배정 보너스: 현재 작업 횟수가 최대 횟수보다 적을수록 가중치 부여
        double balanceBonus = (maxTaskCount - getTaskCount(taskId)) * (double) totalParticipants;

        return gapScore + balanceBonus;
    }

    /**
     * 실제 업무 발생 횟수 기준 간격을 계산합니다.
     */
    public long calculateGap(Long taskId, LocalDate date, List<LocalDate> allOccurredDates, int totalParticipants) {
        LocalDate last = getLastDate(taskId);
        return (last == LocalDate.MIN) ? totalParticipants : (allOccurredDates.stream().filter(d -> d.isAfter(last) && d.isBefore(date)).count() + 1);
    }
}
