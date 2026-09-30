package com.seatforge.booking.repository;

import com.seatforge.booking.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}