package com.basketbot.repository;

import com.basketbot.model.FinanceEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FinanceEntryRepository extends JpaRepository<FinanceEntry, Long> {

    List<FinanceEntry> findByTeamIdOrderByEntryDateDesc(Long teamId);

    List<FinanceEntry> findByTeamIdAndEntryDateBetweenOrderByEntryDateDesc(Long teamId, LocalDate from, LocalDate to);
}
