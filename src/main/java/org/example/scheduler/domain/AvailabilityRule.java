package org.example.scheduler.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "availability_rules")
@Getter
@Setter
@NoArgsConstructor
public class AvailabilityRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private Participant participant;

    @Column(nullable = false)
    private String ruleType; // EVEN_DAYS, ODD_DAYS, WEEKDAY_ONLY, N_DAY_CYCLE 등

    private String ruleValue; // JSON or base date for N_DAY_CYCLE

    public AvailabilityRule(Participant participant, String ruleType) {
        this.participant = participant;
        this.ruleType = ruleType;
    }
}
