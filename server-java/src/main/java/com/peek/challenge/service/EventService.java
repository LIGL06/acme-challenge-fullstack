package com.peek.challenge.service;

import com.peek.challenge.dto.EventResponse;
import com.peek.challenge.model.BookingStatus;
import com.peek.challenge.model.Event;
import com.peek.challenge.repository.BookingRepository;
import com.peek.challenge.repository.EventOccupancy;
import com.peek.challenge.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;

    public List<EventResponse> getAllEvents() {
        return toResponses(eventRepository.findAll());
    }

    public Optional<EventResponse> getEventById(Long id) {
        return eventRepository.findById(id)
                .map(event -> EventResponse.from(event, confirmedParticipants(event.getId())));
    }

    public List<EventResponse> getEventsByDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        return toResponses(eventRepository.findByDateRange(startOfDay, endOfDay));
    }

    public List<Event> getEventsByDateRange(LocalDateTime start, LocalDateTime end) {
        return eventRepository.findByDateRange(start, end);
    }

    private int confirmedParticipants(Long eventId) {
        return bookingRepository.sumParticipantsByEventIdAndStatus(eventId, BookingStatus.CONFIRMED);
    }

    private List<EventResponse> toResponses(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }

        List<Long> eventIds = events.stream().map(Event::getId).toList();
        Map<Long, Integer> occupancyByEventId = bookingRepository
                .sumParticipantsByEventIdsAndStatus(eventIds, BookingStatus.CONFIRMED).stream()
                .collect(Collectors.toMap(EventOccupancy::getEventId, EventOccupancy::getTotal));

        return events.stream()
                .map(event -> EventResponse.from(event, occupancyByEventId.getOrDefault(event.getId(), 0)))
                .toList();
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

