package org.example.scheduler.service;

import org.example.scheduler.domain.*;
import org.example.scheduler.repository.ParticipantRepository;
import org.example.scheduler.repository.ScheduleAssignmentRepository;
import org.example.scheduler.repository.TaskDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DistributionServiceTest {

    @Autowired
    private DistributionService distributionService;

    @Autowired
    private TaskDefinitionRepository taskRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private ScheduleAssignmentRepository assignmentRepository;

    private Long taskId;

    @BeforeEach
    void setUp() {
        // 하루 1명만 필요한 업무로 설정하여 가용성 테스트를 단순화
        TaskDefinition task = new TaskDefinition("청소", "WEEKLY", "MON,TUE,WED,THU,FRI", 1);
        taskId = taskRepository.save(task).getId();

        Participant p1 = new Participant("홍길동", LocalDate.of(2026, 1, 1));
        
        // 이영희는 짝수 주기(나머지 0)인 날만 근무 가능
        Participant p2 = new Participant("이영희", LocalDate.of(2026, 1, 1));
        p2.addAvailabilityRule("EVEN_DAYS", "2026-01-01");

        participantRepository.saveAll(List.of(p1, p2));
        
        // 업무에 참여자 매핑 (수정 가능한 리스트로 전달)
        task.setAllowedParticipants(new java.util.ArrayList<>(List.of(p1, p2)));
        taskRepository.save(task);
    }

    @Test
    @DisplayName("가용성 주기 규칙 검증")
    void availabilityRuleTest() {
        // 한 달 전체 배정
        distributionService.distribute(taskId, 2026, 3);

        List<ScheduleAssignment> assignments = assignmentRepository.findAll();
        assertThat(assignments).isNotEmpty();

        Participant p2 = participantRepository.findAll().stream().filter(p -> p.getName().equals("이영희")).findFirst().get();
        List<ScheduleAssignment> p2Assignments = assignments.stream()
                .filter(a -> a.getParticipantId().equals(p2.getId()))
                .toList();

        LocalDate baseDate = LocalDate.of(2026, 1, 1);
        for (ScheduleAssignment a : p2Assignments) {
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(baseDate, a.getAssignedDate());
            // 이영희는 오직 짝수 주기(나머지 0)인 날에만 배정되어야 함
            assertThat(Math.floorMod(daysBetween, 2)).isZero();
        }
    }

    @Test
    @DisplayName("일정 교체 기능 검증")
    void swapTest() {
        Participant p1 = participantRepository.findAll().stream().filter(p -> p.getName().equals("홍길동")).findFirst().get();
        Participant p2 = participantRepository.findAll().stream().filter(p -> p.getName().equals("이영희")).findFirst().get();
        
        // 교체 가능한 날짜 설정 (둘 다 가용한 날짜 선택)
        // 2026-03-02 (월) : daysBetween 60 (짝수) -> 둘 다 가용
        // 2026-03-04 (수) : daysBetween 62 (짝수) -> 둘 다 가용
        LocalDate d1 = LocalDate.of(2026, 3, 2);
        LocalDate d2 = LocalDate.of(2026, 3, 4);
        
        ScheduleAssignment a1 = assignmentRepository.save(new ScheduleAssignment(taskId, d1, p1.getId()));
        ScheduleAssignment a2 = assignmentRepository.save(new ScheduleAssignment(taskId, d2, p2.getId()));
        
        distributionService.swapAssignments(a1.getId(), a2.getId());
        
        ScheduleAssignment updatedA1 = assignmentRepository.findById(a1.getId()).get();
        assertThat(updatedA1.getParticipantId()).isEqualTo(p2.getId());
        assertThat(updatedA1.getStatus()).isEqualTo(AssignmentStatus.MANUAL_FIXED);
    }

    @Test
    @DisplayName("다중 업무 일괄 배분 검증")
    void batchDistributeTest() {
        // 추가 업무 생성
        TaskDefinition task2 = new TaskDefinition("야간", "WEEKLY", "MON,TUE,WED,THU,FRI,SAT,SUN", 1);
        Participant p1 = participantRepository.findAll().stream().filter(p -> p.getName().equals("홍길동")).findFirst().get();
        Participant p2 = participantRepository.findAll().stream().filter(p -> p.getName().equals("이영희")).findFirst().get();
        task2.setAllowedParticipants(new java.util.ArrayList<>(List.of(p1, p2)));
        Long taskId2 = taskRepository.save(task2).getId();

        // 두 업무에 대해 일괄 배분 수행
        distributionService.distribute(List.of(taskId, taskId2), 2026, 4);

        List<ScheduleAssignment> assignments1 = assignmentRepository.findByTaskIdAndAssignedDateBetween(taskId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        List<ScheduleAssignment> assignments2 = assignmentRepository.findByTaskIdAndAssignedDateBetween(taskId2, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(assignments1).isNotEmpty();
        assertThat(assignments2).isNotEmpty();
    }
}
