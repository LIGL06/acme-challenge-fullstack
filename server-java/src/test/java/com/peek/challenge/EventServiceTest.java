package com.peek.challenge;

import com.peek.challenge.dto.EventResponse;
import com.peek.challenge.model.Event;
import com.peek.challenge.repository.EventRepository;
import com.peek.challenge.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventServiceTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    @Test
    void shouldCreateEvent() {
        Event event = Event.builder()
                .title("Test Event")
                .start(LocalDateTime.now())
                .duration(60)
                .capacity(12)
                .pricePerPerson(new BigDecimal("45.00"))
                .build();

        Event saved = eventService.createEvent(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("Test Event");
    }

    @Test
    void shouldFindEventsByDate() {
        LocalDate today = LocalDate.now();
        Event event = Event.builder()
                .title("Today's Event")
                .start(today.atTime(10, 0))
                .duration(60)
                .capacity(12)
                .pricePerPerson(new BigDecimal("45.00"))
                .build();
        eventRepository.save(event);

        List<EventResponse> events = eventService.getEventsByDate(today);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).title()).isEqualTo("Today's Event");
    }

    @Test
    void shouldReturnAllEvents() {
        eventRepository.save(Event.builder()
                .title("Event 1")
                .start(LocalDateTime.now())
                .duration(30)
                .capacity(10)
                .pricePerPerson(new BigDecimal("30.00"))
                .build());
        eventRepository.save(Event.builder()
                .title("Event 2")
                .start(LocalDateTime.now().plusHours(1))
                .duration(45)
                .capacity(20)
                .pricePerPerson(new BigDecimal("50.00"))
                .build());

        List<EventResponse> events = eventService.getAllEvents();

        assertThat(events).hasSize(2);
    }

    @Test
    void shouldReportFullAvailabilityWhenNoBookings() {
        Event event = eventRepository.save(Event.builder()
                .title("Empty Event")
                .start(LocalDateTime.now())
                .duration(60)
                .capacity(10)
                .pricePerPerson(new BigDecimal("40.00"))
                .build());

        EventResponse response = eventService.getEventById(event.getId()).orElseThrow();

        assertThat(response.currentBookings()).isZero();
        assertThat(response.availableSeats()).isEqualTo(10);
        assertThat(response.availabilityStatus().name()).isEqualTo("AVAILABLE");
    }
}

