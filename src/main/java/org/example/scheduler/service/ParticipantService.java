package org.example.scheduler.service;

import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.Participant;
import org.example.scheduler.domain.TaskDefinition;
import org.example.scheduler.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParticipantService {

    private final ParticipantRepository participantRepository;
    private final UnavailableRangeRepository rangeRepository;
    private final AvailabilityRuleRepository ruleRepository;
    private final TaskDefinitionRepository taskRepository;
    private final ScheduleAssignmentRepository assignmentRepository;

    @Transactional(readOnly = true)
    public List<Participant> findAll() {
        return participantRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Participant findById(Long id) {
        return participantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("참여자를 찾을 수 없습니다. ID: " + id));
    }

    @Transactional
    public void addParticipant(String name, LocalDate joinDate) {
        participantRepository.save(new Participant(name, joinDate));
    }

    @Transactional
    public void deleteParticipant(Long id) {
        Participant p = findById(id);

        // 1. 업무 허용 목록에서 제거
        List<TaskDefinition> tasks = taskRepository.findAll();
        for (TaskDefinition task : tasks) {
            if (task.getAllowedParticipants().contains(p)) {
                task.getAllowedParticipants().remove(p);
                taskRepository.save(task);
            }
        }

        // 2. 배정 기록 삭제 (참여자 ID 기반)
        // ScheduleAssignmentRepository에 해당 참여자 ID로 삭제하는 메서드가 필요할 수 있음
        // 여기서는 수동으로 필터링하여 삭제하거나 Repository 메서드 호출
        assignmentRepository.deleteAll(
            assignmentRepository.findAll().stream()
                .filter(a -> a.getParticipantId().equals(id))
                .toList()
        );

        // 3. 참여자 삭제 (CascadeType.ALL에 의해 규칙/기간도 자동 삭제됨)
        participantRepository.delete(p);
    }

    @Transactional
    public void addUnavailableRange(Long participantId, LocalDate start, LocalDate end) {
        Participant p = findById(participantId);
        p.addUnavailableRange(start, end);
    }

    @Transactional
    public void deleteUnavailableRange(Long id) {
        rangeRepository.deleteById(id);
    }

    @Transactional
    public void addAvailabilityRule(Long participantId, String ruleType, String ruleValue) {
        Participant p = findById(participantId);
        p.addAvailabilityRule(ruleType, ruleValue);
    }

    @Transactional
    public void deleteAvailabilityRule(Long id) {
        ruleRepository.deleteById(id);
    }
}
