package com.peek.challenge.repository;

import com.peek.challenge.model.Booking;
import com.peek.challenge.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByEventId(Long eventId);

    List<Booking> findByEventIdAndStatusOrderByCreatedAtAsc(Long eventId, BookingStatus status);

    @Query("SELECT COALESCE(SUM(b.participantCount), 0) FROM Booking b " +
            "WHERE b.event.id = :eventId AND b.status = :status")
    int sumParticipantsByEventIdAndStatus(@Param("eventId") Long eventId, @Param("status") BookingStatus status);

    @Query("SELECT b.event.id AS eventId, COALESCE(SUM(b.participantCount), 0) AS total FROM Booking b " +
            "WHERE b.event.id IN :eventIds AND b.status = :status GROUP BY b.event.id")
    List<EventOccupancy> sumParticipantsByEventIdsAndStatus(
            @Param("eventIds") List<Long> eventIds, @Param("status") BookingStatus status);

    // Row COUNT of bookings (not a participant sum) for a given status across a set of
    // event ids. Used for the dashboard's top-level "number of waitlisted bookings today"
    // metric, which is intentionally a booking count -- contrast with
    // sumParticipantsByEventIdsAndStatus, which sums participantCount (guests) and is used
    // for the per-event "waitlisted guests" figures.
    long countByEventIdInAndStatus(List<Long> eventIds, BookingStatus status);
}

