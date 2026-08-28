package com.peek.challenge.dto;

import com.peek.challenge.model.Booking;
import com.peek.challenge.model.BookingStatus;

import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        Long eventId,
        String firstName,
        String lastName,
        String customerEmail,
        Integer participantCount,
        String notes,
        BookingStatus status,
        String message,
        LocalDateTime createdAt
) {

    public static BookingResponse from(Booking booking) {
        String message = switch (booking.getStatus()) {
            case WAITLISTED -> "This event is full. The booking has been added to the waitlist.";
            case CANCELLED -> "This booking has been cancelled.";
            case CONFIRMED -> "Booking confirmed.";
        };

        return new BookingResponse(
                booking.getId(),
                booking.getEvent().getId(),
                booking.getFirstName(),
                booking.getLastName(),
                booking.getCustomerEmail(),
                booking.getParticipantCount(),
                booking.getNotes(),
                booking.getStatus(),
                message,
                booking.getCreatedAt()
        );
    }
}
