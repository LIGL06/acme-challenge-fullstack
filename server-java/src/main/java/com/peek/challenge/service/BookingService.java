package com.peek.challenge.service;

import com.peek.challenge.dto.BookingResponse;
import com.peek.challenge.dto.CreateBookingRequest;
import com.peek.challenge.model.Booking;
import com.peek.challenge.model.BookingStatus;
import com.peek.challenge.model.Event;
import com.peek.challenge.repository.BookingRepository;
import com.peek.challenge.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;

    public List<BookingResponse> getBookingsByEventId(Long eventId) {
        return bookingRepository.findByEventId(eventId).stream()
                .map(BookingResponse::from)
                .toList();
    }

    public Optional<Booking> getBookingById(Long id) {
        return bookingRepository.findById(id);
    }

    /**
     * Cancels a booking. If it was CONFIRMED, frees its slots and promotes
     * waitlisted bookings (oldest first) that now fit within the freed
     * capacity, skipping any that don't fit and trying the next one.
     * Cancelling an already-cancelled booking is a no-op.
     *
     * @return The (now cancelled) booking, or empty if it doesn't exist
     */
    @Transactional
    public Optional<Booking> cancelBooking(Long id) {
        return getBookingById(id).map(booking -> {
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                return booking;
            }

            boolean wasConfirmed = booking.getStatus() == BookingStatus.CONFIRMED;
            booking.setStatus(BookingStatus.CANCELLED);
            Booking cancelled = bookingRepository.save(booking);

            if (wasConfirmed) {
                promoteWaitlist(cancelled.getEvent());
            }

            return cancelled;
        });
    }

    private void promoteWaitlist(Event event) {
        int confirmedParticipants = bookingRepository
                .sumParticipantsByEventIdAndStatus(event.getId(), BookingStatus.CONFIRMED);
        int freeSlots = event.getCapacity() - confirmedParticipants;
        if (freeSlots <= 0) {
            return;
        }

        List<Booking> waitlisted = bookingRepository
                .findByEventIdAndStatusOrderByCreatedAtAsc(event.getId(), BookingStatus.WAITLISTED);

        for (Booking candidate : waitlisted) {
            if (freeSlots <= 0) {
                break;
            }
            if (candidate.getParticipantCount() <= freeSlots) {
                candidate.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(candidate);
                freeSlots -= candidate.getParticipantCount();
            }
        }
    }

    /**
     * Creates a booking for the given event. Confirms it if enough capacity
     * remains, otherwise places it on the waitlist.
     *
     * @return The created booking, or empty if the event doesn't exist
     */
    @Transactional
    public Optional<Booking> createBooking(CreateBookingRequest request) {
        return eventRepository.findById(request.eventId())
                .map(event -> {
                    int confirmedParticipants = bookingRepository
                            .sumParticipantsByEventIdAndStatus(event.getId(), BookingStatus.CONFIRMED);
                    boolean fits = confirmedParticipants + request.participantCount() <= event.getCapacity();

                    Booking booking = Booking.builder()
                            .event(event)
                            .firstName(request.firstName())
                            .lastName(request.lastName())
                            .customerEmail(request.customerEmail())
                            .participantCount(request.participantCount())
                            .notes(request.notes())
                            .status(fits ? BookingStatus.CONFIRMED : BookingStatus.WAITLISTED)
                            .build();
                    return bookingRepository.save(booking);
                });
    }
}

