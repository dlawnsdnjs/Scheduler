package org.example.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.example.scheduler.domain.*;
import org.example.scheduler.dto.ExportDataDto;
import org.example.scheduler.repository.ParticipantRepository;
import org.example.scheduler.repository.ScheduleAssignmentRepository;
import org.example.scheduler.repository.TaskDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataMigrationService {

    private final ParticipantRepository participantRepository;
    private final ScheduleAssignmentRepository assignmentRepository;
    private final TaskDefinitionRepository taskRepository;
    private final DataExporter exporter;
    private final DataImporter importer;

    @Transactional(readOnly = true)
    public String exportFullDataJson(LocalDate start, LocalDate end) {
        return exporter.export(participantRepository.findAll(), taskRepository.findAll(), 
                (start != null && end != null) ? assignmentRepository.findByAssignedDateBetween(start, end) : assignmentRepository.findAll());
    }

    @Transactional
    public void importFullDataJson(String json) {
        importer.importData(json);
    }
}
