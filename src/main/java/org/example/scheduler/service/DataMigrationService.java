package org.example.scheduler.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.Participant;
import org.example.scheduler.dto.ParticipantStatsDto;
import org.example.scheduler.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataMigrationService {

    private final ParticipantRepository participantRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Transactional(readOnly = true)
    public String exportStatsJson() {
        try {
            List<Participant> participants = participantRepository.findAll();
            List<ParticipantStatsDto> stats = participants.stream()
                    .map(p -> new ParticipantStatsDto(p.getName(), p.getTaskTotalCounts(), p.getTaskLastAssignedDates()))
                    .collect(Collectors.toList());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(stats);
        } catch (Exception e) {
            throw new RuntimeException("데이터 내보내기 실패", e);
        }
    }

    @Transactional
    public void importStatsJson(String json) {
        try {
            List<ParticipantStatsDto> stats = objectMapper.readValue(json, new TypeReference<List<ParticipantStatsDto>>() {});
            for (ParticipantStatsDto s : stats) {
                participantRepository.findAll().stream()
                        .filter(p -> p.getName().equals(s.getName()))
                        .findFirst()
                        .ifPresent(p -> {
                            p.setTaskTotalCounts(s.getTaskTotalCounts());
                            p.setTaskLastAssignedDates(s.getTaskLastAssignedDates());
                            participantRepository.save(p);
                        });
            }
        } catch (Exception e) {
            throw new RuntimeException("데이터 가져오기 실패", e);
        }
    }
}
