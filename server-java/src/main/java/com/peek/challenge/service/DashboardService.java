package com.peek.challenge.service;

import com.peek.challenge.dto.DashboardEventSummary;
import com.peek.challenge.dto.DashboardResponse;
import com.peek.challenge.dto.EventResponse;
import com.peek.challenge.model.BookingStatus;
import com.peek.challenge.repository.BookingRepository;
import com.peek.challenge.repository.EventOccupancy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final EventService eventService;
    private final BookingRepository bookingRepository;

    public DashboardResponse getTodayDashboard() {
        LocalDate today = LocalDate.now();
        List<EventResponse> events = eventService.getEventsByDate(today);

        if (events.isEmpty()) {
            return new DashboardResponse(today, 0, 0, 0, 0, List.of());
        }

        List<Long> eventIds = events.stream().map(EventResponse::id).toList();

        // Per-event waitlisted GUEST sums (participantCount), batched the same way
        // EventService batches its CONFIRMED sums.
        Map<Long, Integer> waitlistedGuestsByEventId = bookingRepository
                .sumParticipantsByEventIdsAndStatus(eventIds, BookingStatus.WAITLISTED).stream()
                .collect(Collectors.toMap(EventOccupancy::getEventId, EventOccupancy::getTotal));

        // Top-level waitlisted BOOKING row count -- deliberately not a guest sum.
        long totalWaitlistedBookings = bookingRepository.countByEventIdInAndStatus(eventIds, BookingStatus.WAITLISTED);

        int totalConfirmedParticipants = events.stream().mapToInt(EventResponse::currentBookings).sum();
        int totalAvailableSeats = events.stream().mapToInt(EventResponse::availableSeats).sum();

        List<DashboardEventSummary> eventSummaries = events.stream()
                .map(event -> DashboardEventSummary.from(event, waitlistedGuestsByEventId.getOrDefault(event.id(), 0)))
                .toList();

        return new DashboardResponse(
                today,
                events.size(),
                totalConfirmedParticipants,
                totalAvailableSeats,
                (int) totalWaitlistedBookings,
                eventSummaries
        );
    }
}
