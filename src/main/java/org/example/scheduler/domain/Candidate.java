package org.example.scheduler.domain;

import lombok.Getter;
import java.time.LocalDate;

@Getter
public class Candidate {
    private final Participant participant;
    private final double score;
    private final long gap;

    public Candidate(Participant participant, double score, long gap) {
        this.participant = participant;
        this.score = score;
        this.gap = gap;
    }

    public Long getParticipantId() {
        return participant.getId();
    }
}
