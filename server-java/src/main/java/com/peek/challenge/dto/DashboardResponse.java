package com.peek.challenge.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        LocalDate date,
        int totalEvents,
        int totalConfirmedParticipants,
        int totalAvailableSeats,
        // Row COUNT of bookings with status WAITLISTED across today's events -- NOT a
        // sum of participantCount. Do not "fix" this to match totalConfirmedParticipants'
        // guest-sum shape; see DashboardEventSummary.waitlistedGuests for the per-event
        // guest-sum figure that intentionally differs from this one.
        int totalWaitlistedBookings,
        List<DashboardEventSummary> events
) {
}
