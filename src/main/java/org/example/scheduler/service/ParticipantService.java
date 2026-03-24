package org.example.scheduler.service;

import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.Participant;
import org.example.scheduler.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParticipantService {

    private final ParticipantRepository participantRepository;

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
        participantRepository.deleteById(id);
    }

    @Transactional
    public void addUnavailableRange(Long participantId, LocalDate start, LocalDate end) {
        Participant p = findById(participantId);
        p.addUnavailableRange(start, end);
    }

    @Transactional
    public void addAvailabilityRule(Long participantId, String ruleType) {
        Participant p = findById(participantId);
        p.addAvailabilityRule(ruleType);
    }
}
