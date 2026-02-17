package com.basketbot.repository;

import com.basketbot.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByTeamId(Long teamId);

    List<Match> findByTeamIdOrderByDateDesc(Long teamId);

    Optional<Match> findFirstByTeamIdAndStatusOrderByDateDesc(Long teamId, Match.Status status);

    Optional<Match> findFirstByTeamIdAndStatusAndDateAfterOrderByDateAsc(Long teamId, Match.Status status, Instant after);

    @Query("SELECT m FROM Match m JOIN FETCH m.team WHERE m.status = 'SCHEDULED' AND m.date BETWEEN :from AND :to AND m.reminder24hSent = false")
    List<Match> findFor24hReminder(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT m FROM Match m JOIN FETCH m.team WHERE m.status = 'SCHEDULED' AND m.date BETWEEN :from AND :to AND m.reminder3hSent = false")
    List<Match> findFor3hReminder(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT m FROM Match m JOIN FETCH m.team WHERE m.status = 'SCHEDULED' AND m.date BETWEEN :from AND :to AND m.reminderAfterSent = false")
    List<Match> findForAfterMatchReminder(@Param("from") Instant from, @Param("to") Instant to);
}
