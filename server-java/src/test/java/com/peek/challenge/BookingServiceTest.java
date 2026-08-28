package com.peek.challenge;

import com.peek.challenge.dto.BookingResponse;
import com.peek.challenge.dto.CreateBookingRequest;
import com.peek.challenge.model.Booking;
import com.peek.challenge.model.BookingStatus;
import com.peek.challenge.model.Event;
import com.peek.challenge.repository.BookingRepository;
import com.peek.challenge.repository.EventRepository;
import com.peek.challenge.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingServiceTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventRepository eventRepository;

    private Event event;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        eventRepository.deleteAll();

        event = eventRepository.save(Event.builder()
                .title("Wine tour")
                .start(LocalDateTime.now())
                .duration(60)
                .capacity(10)
                .pricePerPerson(new BigDecimal("45.00"))
                .build());
    }

    private Booking confirmedBooking(int participantCount) {
        Booking booking = bookingService.createBooking(new CreateBookingRequest(
                event.getId(), "Jane", "Doe", "jane@example.com", participantCount, null
        )).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        return booking;
    }

    private Booking waitlistedBooking(int participantCount) {
        Booking booking = bookingService.createBooking(new CreateBookingRequest(
                event.getId(), "Alex", "Roe", "alex@example.com", participantCount, null
        )).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.WAITLISTED);
        return booking;
    }

    @Test
    void shouldReturnEmptyWhenCancellingUnknownBooking() {
        assertThat(bookingService.cancelBooking(999_999L)).isEmpty();
    }

    @Test
    void shouldCancelConfirmedBookingWithNoWaitlist() {
        Booking booking = confirmedBooking(4);

        Booking cancelled = bookingService.cancelBooking(booking.getId()).orElseThrow();

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancellingAlreadyCancelledBookingIsANoOp() {
        Booking booking = confirmedBooking(4);
        bookingService.cancelBooking(booking.getId());

        Booking result = bookingService.cancelBooking(booking.getId()).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancellingAWaitlistedBookingDoesNotTriggerPromotion() {
        confirmedBooking(10); // fills capacity
        Booking waitlisted = waitlistedBooking(5);

        Booking result = bookingService.cancelBooking(waitlisted.getId()).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        // no capacity was freed, so no promotion should have happened for anyone else
    }

    @Test
    void cancellingConfirmedBookingPromotesOldestWaitlistedThatFits() {
        Booking confirmed = confirmedBooking(10); // fills capacity (10/10)
        Booking waitlisted = waitlistedBooking(6); // exceeds capacity while confirmed holds 10

        bookingService.cancelBooking(confirmed.getId());

        Booking promoted = bookingRepository.findById(waitlisted.getId()).orElseThrow();
        assertThat(promoted.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void cancellingConfirmedBookingSkipsOversizedEntryAndPromotesNextThatFits() {
        Booking a = confirmedBooking(4);
        Booking b = confirmedBooking(6); // event now full: 10/10
        Booking oversized = waitlistedBooking(5); // oldest waitlisted, won't fit in 4 freed slots
        Booking smaller = waitlistedBooking(3); // fits in 4 freed slots

        bookingService.cancelBooking(a.getId()); // frees 4 slots

        Booking oversizedResult = bookingRepository.findById(oversized.getId()).orElseThrow();
        Booking smallerResult = bookingRepository.findById(smaller.getId()).orElseThrow();

        assertThat(oversizedResult.getStatus()).isEqualTo(BookingStatus.WAITLISTED);
        assertThat(smallerResult.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void getBookingsByEventIdReturnsProperlyMappedBookingResponse() {
        Booking booking = confirmedBooking(4);

        List<BookingResponse> responses = bookingService.getBookingsByEventId(event.getId());

        assertThat(responses).hasSize(1);
        BookingResponse response = responses.get(0);
        assertThat(response.id()).isEqualTo(booking.getId());
        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.message()).isEqualTo("Booking confirmed.");
    }

    @Test
    void getBookingsByEventIdIncludesBookingsOfEveryStatusWithoutFiltering() {
        Booking confirmed = confirmedBooking(8);
        Booking toCancel = confirmedBooking(2); // fills capacity (10/10)
        Booking waitlisted = waitlistedBooking(5); // exceeds capacity while confirmed holds 10
        bookingService.cancelBooking(toCancel.getId()); // frees only 2 slots, not enough to promote

        List<BookingResponse> responses = bookingService.getBookingsByEventId(event.getId());

        assertThat(responses).extracting(BookingResponse::id)
                .containsExactlyInAnyOrder(confirmed.getId(), toCancel.getId(), waitlisted.getId());
        assertThat(responses).extracting(BookingResponse::status)
                .containsExactlyInAnyOrder(BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.WAITLISTED);
    }
}
