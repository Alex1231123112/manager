package com.basketbot.repository;

import com.basketbot.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByTeamIdOrderByEventDateDesc(Long teamId);

    List<Event> findByTeamIdAndEventDateBetweenOrderByEventDateAsc(Long teamId, Instant from, Instant to);
}
