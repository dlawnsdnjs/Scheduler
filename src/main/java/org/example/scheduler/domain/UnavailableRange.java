package org.example.scheduler.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "unavailable_ranges")
@Getter
@Setter
@NoArgsConstructor
public class UnavailableRange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private Participant participant;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    public UnavailableRange(Participant participant, LocalDate startDate, LocalDate endDate) {
        this.participant = participant;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
