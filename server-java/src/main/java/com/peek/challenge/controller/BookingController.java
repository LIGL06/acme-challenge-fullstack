package com.peek.challenge.controller;

import com.peek.challenge.model.Booking;
import com.peek.challenge.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class BookingController {

    private final BookingService bookingService;

    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<Booking>> getBookingsByEventId(@PathVariable Long eventId) {
        return ResponseEntity.ok(bookingService.getBookingsByEventId(eventId));
    }

    // Candidate TODO: Implement POST endpoint to create a booking
    // The BookingService.createBooking method is already implemented and ready to use.
    // Create a DTO for the request body and wire up the endpoint.
}

