package com.peek.challenge.service;

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

    public List<Booking> getBookingsByEventId(Long eventId) {
        return bookingRepository.findByEventId(eventId);
    }

    public Optional<Booking> getBookingById(Long id) {
        return bookingRepository.findById(id);
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

    @Transactional
    public boolean deleteBooking(Long id) {
        return bookingRepository.findById(id)
                .map(booking -> {
                    bookingRepository.delete(booking);
                    return true;
                })
                .orElse(false);
    }
}

