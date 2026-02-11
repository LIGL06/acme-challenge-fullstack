package com.peek.challenge.config;

import com.peek.challenge.model.Event;
import com.peek.challenge.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the database with sample events on application startup.
 * Only runs when the 'seed' profile is active.
 * 
 * Run with: ./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final EventRepository eventRepository;

    private static final List<LocalTime> EVENT_TIMES = List.of(
            LocalTime.of(10, 0),
            LocalTime.of(14, 0),
            LocalTime.of(16, 0)
    );

    private static final List<DayOfWeek> WEEKDAYS = List.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
    );

    @Override
    public void run(String... args) {
        if (eventRepository.count() > 0) {
            log.info("Database already contains events, skipping seed.");
            return;
        }

        log.info("Seeding database with sample events...");

        List<Event> events = new ArrayList<>();
        LocalDate startDate = LocalDate.now();

        // Generate events for the next 20 weeks (100 days = ~300 events)
        for (int week = 0; week < 20; week++) {
            for (DayOfWeek day : WEEKDAYS) {
                LocalDate eventDate = startDate.plusWeeks(week).with(day);
                for (LocalTime time : EVENT_TIMES) {
                    Event event = Event.builder()
                            .title("Wine tour")
                            .start(LocalDateTime.of(eventDate, time))
                            .duration(60)
                            .build();
                    events.add(event);
                }
            }
        }

        eventRepository.saveAll(events);
        log.info("Seeded {} events.", events.size());
    }
}

