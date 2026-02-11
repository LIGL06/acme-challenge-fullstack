package com.peek.challenge.service;

import com.peek.challenge.model.Booking;
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
     * Creates a booking for a given event.
     * Candidate TODO: Implement the REST endpoint that calls this method.
     *
     * @param eventId   The ID of the event to book
     * @param firstName Guest's first name
     * @param lastName  Guest's last name
     * @return The created booking, or empty if the event doesn't exist
     */
    @Transactional
    public Optional<Booking> createBooking(Long eventId, String firstName, String lastName) {
        return eventRepository.findById(eventId)
                .map(event -> {
                    Booking booking = Booking.builder()
                            .event(event)
                            .firstName(firstName)
                            .lastName(lastName)
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

