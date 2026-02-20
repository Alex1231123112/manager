package com.basketbot.repository;

import com.basketbot.model.LeagueTableRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeagueTableRowRepository extends JpaRepository<LeagueTableRow, Long> {

    List<LeagueTableRow> findByTeamIdOrderByPositionAsc(Long teamId);
}
