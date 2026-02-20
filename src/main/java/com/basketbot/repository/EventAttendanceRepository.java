package com.basketbot.repository;

import com.basketbot.model.EventAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventAttendanceRepository extends JpaRepository<EventAttendance, Long> {

    List<EventAttendance> findByMatchId(Long matchId);

    Optional<EventAttendance> findByMatchIdAndTelegramUserId(Long matchId, String telegramUserId);

    List<EventAttendance> findByTelegramUserId(String telegramUserId);
}
