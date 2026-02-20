package com.basketbot.repository;

import com.basketbot.model.IntegrationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface IntegrationEventRepository extends JpaRepository<IntegrationEvent, Long> {

    List<IntegrationEvent> findTop100ByOrderByCreatedAtDesc();

    @Query("SELECT e FROM IntegrationEvent e WHERE e.createdAt >= :from AND e.createdAt <= :to ORDER BY e.createdAt DESC")
    List<IntegrationEvent> findByCreatedAtBetween(Instant from, Instant to);

    long countByEventTypeAndSuccess(IntegrationEvent.EventType eventType, boolean success);

    long countBySuccess(boolean success);
}
