package com.seatforge.booking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seats")
@Getter
@Setter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String section;

    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Column(name = "price_cents", nullable = false)
    private Integer priceCents;


    public static Seat create(Event event, String section, String rowLabel, Integer seatNumber, Integer priceCents) {
        Seat s = new Seat();
        s.event = event;
        s.section = section;
        s.rowLabel = rowLabel;
        s.seatNumber = seatNumber;
        s.priceCents = priceCents;
        return s;
    }
}
