package org.example.scheduler.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Collections;
import static org.assertj.core.api.Assertions.assertThat;

class ParticipantTest {
    @Test
    void testCalculateScore() {
        Participant p = new Participant("Test", LocalDate.now());
        double score = p.calculateScore(1L, LocalDate.now(), Collections.emptyList(), 5, 1);
        assertThat(score).isGreaterThanOrEqualTo(0);
    }
}
