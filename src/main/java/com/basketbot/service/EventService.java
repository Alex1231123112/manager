package com.basketbot.service;

import com.basketbot.model.Event;
import com.basketbot.model.Team;
import com.basketbot.repository.EventRepository;
import com.basketbot.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final TeamRepository teamRepository;

    public EventService(EventRepository eventRepository, TeamRepository teamRepository) {
        this.eventRepository = eventRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public List<Event> findByTeamId(Long teamId) {
        return eventRepository.findByTeamIdOrderByEventDateDesc(teamId);
    }

    @Transactional(readOnly = true)
    public List<Event> findByTeamIdAndDateBetween(Long teamId, Instant from, Instant to) {
        return eventRepository.findByTeamIdAndEventDateBetweenOrderByEventDateAsc(teamId, from, to);
    }

    @Transactional(readOnly = true)
    public Optional<Event> findByIdAndTeamId(Long id, Long teamId) {
        return eventRepository.findById(id)
                .filter(e -> e.getTeam().getId().equals(teamId));
    }

    @Transactional
    public Event create(Long teamId, String title, Event.EventType eventType, Instant eventDate, String location, String description) {
        Team team = teamRepository.getReferenceById(teamId);
        Event e = new Event();
        e.setTeam(team);
        e.setTitle(title != null && !title.isBlank() ? title.trim() : "Событие");
        e.setEventType(eventType != null ? eventType : Event.EventType.TRAINING);
        e.setEventDate(eventDate != null ? eventDate : Instant.now());
        e.setLocation(location != null && !location.isBlank() ? location.trim() : null);
        e.setDescription(description != null && !description.isBlank() ? description.trim() : null);
        return eventRepository.save(e);
    }

    @Transactional
    public void deleteByIdAndTeamId(Long id, Long teamId) {
        findByIdAndTeamId(id, teamId).ifPresent(eventRepository::delete);
    }
}
