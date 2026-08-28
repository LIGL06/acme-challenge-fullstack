package com.peek.challenge.config;

import com.peek.challenge.model.Booking;
import com.peek.challenge.model.BookingStatus;
import com.peek.challenge.model.Event;
import com.peek.challenge.repository.BookingRepository;
import com.peek.challenge.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
    private final BookingRepository bookingRepository;

    private static final int DEFAULT_CAPACITY = 12;
    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("45.00");

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
                            .capacity(DEFAULT_CAPACITY)
                            .pricePerPerson(DEFAULT_PRICE)
                            .build();
                    events.add(event);
                }
            }
        }

        eventRepository.saveAll(events);
        log.info("Seeded {} events.", events.size());

        seedSampleBookings(startDate);
    }

    /**
     * Seeds a few bookings on today's events so the availability indicator
     * (available/limited/full) is visible without manually creating bookings.
     */
    private void seedSampleBookings(LocalDate startDate) {
        List<Event> todaysEvents = eventRepository.findByDateRange(
                startDate.atStartOfDay(), startDate.plusDays(1).atStartOfDay());

        if (todaysEvents.size() < 3) {
            return;
        }

        List<Booking> bookings = new ArrayList<>();

        // First event stays untouched -> AVAILABLE (green)

        // Second event: partially booked -> LIMITED (yellow)
        bookings.add(sampleBooking(todaysEvents.get(1), "Jamie", "Lee", 7, BookingStatus.CONFIRMED));

        // Third event: fully booked, plus one guest on the waitlist -> FULL (red)
        Event fullEvent = todaysEvents.get(2);
        bookings.add(sampleBooking(fullEvent, "Sam", "Rivera", fullEvent.getCapacity(), BookingStatus.CONFIRMED));
        bookings.add(sampleBooking(fullEvent, "Taylor", "Nguyen", 2, BookingStatus.WAITLISTED));

        bookingRepository.saveAll(bookings);
        log.info("Seeded {} sample bookings.", bookings.size());
    }

    private Booking sampleBooking(Event event, String firstName, String lastName, int participantCount, BookingStatus status) {
        return Booking.builder()
                .event(event)
                .firstName(firstName)
                .lastName(lastName)
                .customerEmail((firstName + "." + lastName + "@example.com").toLowerCase())
                .participantCount(participantCount)
                .status(status)
                .build();
    }
}

