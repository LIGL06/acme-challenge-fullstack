package com.peek.challenge.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
        @NotNull(message = "Event id is required")
        Long eventId,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotBlank(message = "Customer email is required")
        @Email(message = "Customer email must be valid")
        String customerEmail,

        @NotNull(message = "Participant count is required")
        @Min(value = 1, message = "Participant count must be at least 1")
        Integer participantCount,

        String notes
) {
}
