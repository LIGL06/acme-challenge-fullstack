package com.peek.challenge.dto;

import com.peek.challenge.model.AvailabilityStatus;
import com.peek.challenge.model.Event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String title,
        LocalDateTime start,
        Integer duration,
        Integer capacity,
        BigDecimal pricePerPerson,
        int currentBookings,
        int availableSeats,
        AvailabilityStatus availabilityStatus
) {

    public static EventResponse from(Event event, int currentBookings) {
        int availableSeats = Math.max(event.getCapacity() - currentBookings, 0);
        double remainingRatio = (double) availableSeats / event.getCapacity();

        AvailabilityStatus status;
        if (availableSeats <= 0) {
            status = AvailabilityStatus.FULL;
        } else if (remainingRatio > 0.5) {
            status = AvailabilityStatus.AVAILABLE;
        } else {
            status = AvailabilityStatus.LIMITED;
        }

        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getStart(),
                event.getDuration(),
                event.getCapacity(),
                event.getPricePerPerson(),
                currentBookings,
                availableSeats,
                status
        );
    }
}
