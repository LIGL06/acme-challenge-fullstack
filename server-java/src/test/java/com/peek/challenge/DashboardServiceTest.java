package com.peek.challenge;

import com.peek.challenge.dto.CreateBookingRequest;
import com.peek.challenge.dto.DashboardEventSummary;
import com.peek.challenge.dto.DashboardResponse;
import com.peek.challenge.model.Booking;
import com.peek.challenge.model.BookingStatus;
import com.peek.challenge.model.Event;
import com.peek.challenge.repository.BookingRepository;
import com.peek.challenge.repository.EventRepository;
import com.peek.challenge.service.BookingService;
import com.peek.challenge.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        eventRepository.deleteAll();
    }

    private Event createEvent(String title, int hour, int capacity) {
        return eventRepository.save(Event.builder()
                .title(title)
                .start(LocalDate.now().atTime(hour, 0))
                .duration(60)
                .capacity(capacity)
                .pricePerPerson(new BigDecimal("40.00"))
                .build());
    }

    private Booking confirmedBooking(Event event, int participantCount) {
        Booking booking = bookingService.createBooking(new CreateBookingRequest(
                event.getId(), "Jane", "Doe", "jane@example.com", participantCount, null
        )).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        return booking;
    }

    private Booking waitlistedBooking(Event event, int participantCount) {
        Booking booking = bookingService.createBooking(new CreateBookingRequest(
                event.getId(), "Alex", "Roe", "alex@example.com", participantCount, null
        )).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.WAITLISTED);
        return booking;
    }

    @Test
    void shouldReturnAllZeroSummaryWhenNoEventsToday() {
        DashboardResponse dashboard = dashboardService.getTodayDashboard();

        assertThat(dashboard.date()).isEqualTo(LocalDate.now());
        assertThat(dashboard.totalEvents()).isZero();
        assertThat(dashboard.totalConfirmedParticipants()).isZero();
        assertThat(dashboard.totalAvailableSeats()).isZero();
        assertThat(dashboard.totalWaitlistedBookings()).isZero();
        assertThat(dashboard.events()).isEmpty();
    }

    @Test
    void totalWaitlistedBookingsIsARowCountNotAGuestSum() {
        // Event A: fully booked (10/10 confirmed), plus two waitlisted bookings of 3
        // and 5 participants each.
        Event eventA = createEvent("Wine Tour", 10, 10);
        confirmedBooking(eventA, 10); // fills capacity
        waitlistedBooking(eventA, 3);
        waitlistedBooking(eventA, 5);

        // Event B: 4/12 confirmed, no waitlist.
        Event eventB = createEvent("Cheese Tasting", 14, 12);
        confirmedBooking(eventB, 4);

        DashboardResponse dashboard = dashboardService.getTodayDashboard();

        assertThat(dashboard.totalEvents()).isEqualTo(2);
        assertThat(dashboard.totalConfirmedParticipants()).isEqualTo(14); // 10 + 4 guest sum
        assertThat(dashboard.totalAvailableSeats()).isEqualTo(8); // 0 (A) + 8 (B)

        // The top-level metric is a COUNT of waitlisted booking rows (2), NOT the sum
        // of their participantCounts (3 + 5 = 8). This is the key assertion of this test.
        assertThat(dashboard.totalWaitlistedBookings()).isEqualTo(2);
    }

    @Test
    void perEventWaitlistedGuestsIsASumOfParticipantsContrastingWithTopLevelCount() {
        Event event = createEvent("Wine Tour", 10, 10);
        confirmedBooking(event, 10); // fills capacity
        waitlistedBooking(event, 3);
        waitlistedBooking(event, 5);

        DashboardResponse dashboard = dashboardService.getTodayDashboard();

        assertThat(dashboard.events()).hasSize(1);
        DashboardEventSummary summary = dashboard.events().get(0);

        // Per-event waitlistedGuests sums participant counts (3 + 5 = 8), which is
        // intentionally different from the top-level totalWaitlistedBookings row
        // count of 2 asserted in totalWaitlistedBookingsIsARowCountNotAGuestSum().
        assertThat(summary.waitlistedGuests()).isEqualTo(8);
        assertThat(dashboard.totalWaitlistedBookings()).isEqualTo(2);

        assertThat(summary.currentBookings()).isEqualTo(10);
        assertThat(summary.availableSeats()).isZero();
        assertThat(summary.availabilityStatus().name()).isEqualTo("FULL");
    }
}
