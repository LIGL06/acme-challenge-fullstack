package com.peek.challenge.dto;

import com.peek.challenge.model.AvailabilityStatus;

import java.time.LocalDateTime;

public record DashboardEventSummary(
        Long id,
        String title,
        LocalDateTime start,
        Integer capacity,
        int currentBookings,
        int availableSeats,
        AvailabilityStatus availabilityStatus,
        int waitlistedGuests
) {

    // waitlistedGuests is a sum of participantCount across this event's WAITLISTED
    // bookings (guests) -- contrast with DashboardResponse.totalWaitlistedBookings,
    // which is a row COUNT of waitlisted bookings across all of today's events.
    public static DashboardEventSummary from(EventResponse event, int waitlistedGuests) {
        return new DashboardEventSummary(
                event.id(),
                event.title(),
                event.start(),
                event.capacity(),
                event.currentBookings(),
                event.availableSeats(),
                event.availabilityStatus(),
                waitlistedGuests
        );
    }
}
