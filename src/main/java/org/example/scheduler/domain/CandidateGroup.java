package org.example.scheduler.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CandidateGroup {
    private final List<Participant> participants;
    private List<Candidate> candidates;

    private CandidateGroup(List<Participant> participants) {
        this.participants = new ArrayList<>(participants);
    }

    public static CandidateGroup from(List<Participant> participants) {
        return new CandidateGroup(participants);
    }

    /**
     * 해당 날짜에 가용하지 않은 참여자를 필터링합니다.
     */
    public CandidateGroup filterAvailable(LocalDate date) {
        this.participants.removeIf(p -> !p.isAvailable(date));
        return this;
    }

    /**
     * 충돌 업무가 배정된 참여자를 제외합니다.
     */
    public CandidateGroup excludeConflicting(List<ScheduleAssignment> conflictingAssignments) {
        this.participants.removeIf(p -> 
            conflictingAssignments.stream().anyMatch(a -> a.getParticipantId().equals(p.getId()))
        );
        return this;
    }

    /**
     * 모든 후보자의 점수를 계산하고 정렬합니다.
     */
    public CandidateGroup scoreAll(TaskDefinition task, LocalDate date, List<LocalDate> allOccurredDates, int maxCount) {
        int totalParticipants = this.participants.size();
        this.candidates = this.participants.stream()
                .map(p -> new Candidate(
                        p, 
                        p.calculateScore(task.getId(), date, allOccurredDates, totalParticipants, maxCount),
                        p.calculateGap(task.getId(), date, allOccurredDates, totalParticipants)
                ))
                .sorted(Comparator.comparingDouble(Candidate::getScore).reversed())
                .collect(Collectors.toList());
        return this;
    }

    /**
     * 상위 N명의 후보자를 선택합니다.
     */
    public List<Candidate> getTopCandidates(int needed) {
        if (candidates == null) return new ArrayList<>();
        return candidates.stream()
                .limit(needed)
                .collect(Collectors.toList());
    }
}
