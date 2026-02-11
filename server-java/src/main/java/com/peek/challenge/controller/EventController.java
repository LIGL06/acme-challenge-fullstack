package com.peek.challenge.controller;

import com.peek.challenge.model.Event;
import com.peek.challenge.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<Event>> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Event> getEventById(@PathVariable Long id) {
        return eventService.getEventById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<Event>> getEventsByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(eventService.getEventsByDate(date));
    }

    // Candidate TODO: Add POST endpoint to create events
    // Candidate TODO: Add PUT endpoint to update events
    // Candidate TODO: Add DELETE endpoint to delete events
}

