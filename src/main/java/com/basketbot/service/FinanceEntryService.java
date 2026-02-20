package com.basketbot.service;

import com.basketbot.model.FinanceEntry;
import com.basketbot.model.Team;
import com.basketbot.repository.FinanceEntryRepository;
import com.basketbot.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class FinanceEntryService {

    private final FinanceEntryRepository financeEntryRepository;
    private final TeamRepository teamRepository;

    public FinanceEntryService(FinanceEntryRepository financeEntryRepository,
                               TeamRepository teamRepository) {
        this.financeEntryRepository = financeEntryRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public List<FinanceEntry> findByTeamId(Long teamId) {
        return financeEntryRepository.findByTeamIdOrderByEntryDateDesc(teamId);
    }

    @Transactional(readOnly = true)
    public List<FinanceEntry> findByTeamIdAndDateBetween(Long teamId, LocalDate from, LocalDate to) {
        return financeEntryRepository.findByTeamIdAndEntryDateBetweenOrderByEntryDateDesc(teamId, from, to);
    }

    @Transactional(readOnly = true)
    public Optional<FinanceEntry> findByIdAndTeamId(Long id, Long teamId) {
        return financeEntryRepository.findById(id)
                .filter(e -> e.getTeam().getId().equals(teamId));
    }

    @Transactional
    public FinanceEntry create(Long teamId, FinanceEntry.Type type, BigDecimal amount, String description, LocalDate entryDate) {
        Team team = teamRepository.getReferenceById(teamId);
        FinanceEntry e = new FinanceEntry();
        e.setTeam(team);
        e.setType(type);
        e.setAmount(amount != null ? amount : BigDecimal.ZERO);
        e.setDescription(description);
        e.setEntryDate(entryDate != null ? entryDate : LocalDate.now());
        return financeEntryRepository.save(e);
    }

    @Transactional
    public void deleteByIdAndTeamId(Long id, Long teamId) {
        findByIdAndTeamId(id, teamId).ifPresent(financeEntryRepository::delete);
    }

    /** Сводка за период: приход, расход, баланс. */
    @Transactional(readOnly = true)
    public BigDecimal totalIncome(Long teamId, LocalDate from, LocalDate to) {
        return financeEntryRepository.findByTeamIdAndEntryDateBetweenOrderByEntryDateDesc(teamId, from, to).stream()
                .filter(e -> e.getType() == FinanceEntry.Type.INCOME)
                .map(FinanceEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalExpense(Long teamId, LocalDate from, LocalDate to) {
        return financeEntryRepository.findByTeamIdAndEntryDateBetweenOrderByEntryDateDesc(teamId, from, to).stream()
                .filter(e -> e.getType() == FinanceEntry.Type.EXPENSE)
                .map(FinanceEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
