package com.peek.challenge.service;

import com.peek.challenge.model.Event;
import com.peek.challenge.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;

    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    public Optional<Event> getEventById(Long id) {
        return eventRepository.findById(id);
    }

    public List<Event> getEventsByDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        return eventRepository.findByDateRange(startOfDay, endOfDay);
    }

    public List<Event> getEventsByDateRange(LocalDateTime start, LocalDateTime end) {
        return eventRepository.findByDateRange(start, end);
    }

    @Transactional
    public Event createEvent(Event event) {
        return eventRepository.save(event);
    }

    @Transactional
    public Optional<Event> updateEvent(Long id, Event eventDetails) {
        return eventRepository.findById(id)
                .map(event -> {
                    event.setTitle(eventDetails.getTitle());
                    event.setStart(eventDetails.getStart());
                    event.setDuration(eventDetails.getDuration());
                    return eventRepository.save(event);
                });
    }

    @Transactional
    public boolean deleteEvent(Long id) {
        return eventRepository.findById(id)
                .map(event -> {
                    eventRepository.delete(event);
                    return true;
                })
                .orElse(false);
    }
}

