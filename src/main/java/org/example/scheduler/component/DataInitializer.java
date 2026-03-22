package org.example.scheduler.component;

import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.Participant;
import org.example.scheduler.domain.TaskDefinition;
import org.example.scheduler.repository.ParticipantRepository;
import org.example.scheduler.repository.TaskDefinitionRepository;
import org.example.scheduler.service.DistributionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final TaskDefinitionRepository taskRepository;
    private final ParticipantRepository participantRepository;
    private final DistributionService distributionService;

    @Override
    public void run(String... args) throws Exception {
        // 이미 데이터가 있으면 중복 생성 방지
        if (taskRepository.count() > 0) return;

        // 1. 참여자 생성
        Participant p1 = new Participant("강백호", LocalDate.of(2024, 1, 1));
        Participant p2 = new Participant("서태웅", LocalDate.of(2024, 1, 1));
        Participant p3 = new Participant("송태섭", LocalDate.of(2024, 1, 1));
        p3.addAvailabilityRule("EVEN_DAYS"); 

        Participant p4 = new Participant("정대만", LocalDate.of(2024, 1, 1));
        p4.addUnavailableRange(LocalDate.of(2024, 3, 10), LocalDate.of(2024, 3, 15));

        Participant p5 = new Participant("채치수", LocalDate.of(2024, 1, 1));
        participantRepository.saveAll(List.of(p1, p2, p3, p4, p5));

        // 2. 업무 생성 및 참여자 할당
        // 업무 A: 야간 당직 (월, 수, 금 / 모든 참여자 참여)
        TaskDefinition nightShift = new TaskDefinition("야간 당직", "WEEKLY", "월,수,금", 2);
        nightShift.setAllowedParticipants(List.of(p1, p2, p3, p4, p5));
        nightShift.setColor("#e74c3c"); // 빨간색 계열

        // 업무 B: 사무실 청소 (2일마다 / 강백호, 서태웅만 참여)
        TaskDefinition cleaning = new TaskDefinition("사무실 청소", "INTERVAL", "2", 1);
        cleaning.setAllowedParticipants(List.of(p1, p2));
        cleaning.setColor("#2ecc71"); // 초록색 계열
        
        taskRepository.saveAll(List.of(nightShift, cleaning));

        // 3. 초기 일정 분배 (2024년 3월)
        distributionService.distribute(nightShift.getId(), 2024, 3);
        distributionService.distribute(cleaning.getId(), 2024, 3);
        
        System.out.println(">>> Sample data initialized and initial schedule distributed for March 2024.");
    }
}
