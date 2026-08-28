package com.peek.challenge.controller;

import com.peek.challenge.dto.BookingResponse;
import com.peek.challenge.dto.CreateBookingRequest;
import com.peek.challenge.model.Booking;
import com.peek.challenge.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        return bookingService.createBooking(request)
                .map(booking -> ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(booking)))
                .orElse(ResponseEntity.notFound().build());
    }
}

