import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { EventService } from '../../services/event.service';
import { BookingResponse, Event } from '../../models/event.model';

function byCreatedAtAsc(a: BookingResponse, b: BookingResponse): number {
  return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
}

@Component({
  selector: 'app-booking-management',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './booking-management.component.html',
  styleUrl: './booking-management.component.scss'
})
export class BookingManagementComponent implements OnInit {
  eventId = 0;
  event: Event | null = null;
  loadingEvent = true;
  loadError: string | null = null;
  returnDate: string | null = null;

  mainBookings: BookingResponse[] = [];
  waitlistBookings: BookingResponse[] = [];

  cancellingIds = new Set<number>();
  cancelError: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private eventService: EventService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const id = Number(params.get('id'));

      this.eventId = id;
      this.returnDate = this.route.snapshot.queryParamMap.get('date');
      this.event = null;
      this.mainBookings = [];
      this.waitlistBookings = [];
      this.loadError = null;
      this.cancelError = null;
      this.cancellingIds.clear();

      if (!Number.isFinite(id) || id <= 0) {
        this.loadingEvent = false;
        this.loadError = 'Invalid event.';
        return;
      }

      this.loadData(true);
    });
  }

  loadData(showLoading = true): void {
    if (showLoading) {
      this.loadingEvent = true;
      this.loadError = null;
    }

    forkJoin({
      event: this.eventService.getEventById(this.eventId),
      bookings: this.eventService.getBookingsByEventId(this.eventId)
    }).subscribe({
      next: ({ event, bookings }) => {
        this.event = event;
        this.mainBookings = bookings
          .filter((booking) => booking.status !== 'WAITLISTED')
          .sort(byCreatedAtAsc);
        this.waitlistBookings = bookings
          .filter((booking) => booking.status === 'WAITLISTED')
          .sort(byCreatedAtAsc);
        this.loadingEvent = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loadingEvent = false;

        // Only the initial load surfaces a page-level error here. A background
        // refresh (showLoading === false, e.g. after a cancel that already
        // succeeded) failing to re-fetch shouldn't be reported as a cancel
        // failure — the row was already patched locally with the real outcome.
        if (showLoading) {
          this.loadError = err.status === 404
            ? 'This event could not be found. It may have been removed.'
            : 'Could not load this event. Please try again.';
        }
      }
    });
  }

  cancelBooking(booking: BookingResponse): void {
    this.cancelError = null;
    this.cancellingIds.add(booking.id);

    this.eventService.cancelBooking(booking.id).subscribe({
      next: (updated) => {
        this.cancellingIds.delete(booking.id);
        const index = this.mainBookings.findIndex((b) => b.id === updated.id);
        if (index !== -1) {
          this.mainBookings[index] = updated;
        }
        // Refresh in the background to pick up any waitlist promotions and
        // updated occupancy; this cancellation has already succeeded and is
        // already reflected above regardless of whether this refresh works.
        this.loadData(false);
      },
      error: () => {
        this.cancellingIds.delete(booking.id);
        this.cancelError = 'Could not cancel this booking. Please try again.';
      }
    });
  }

  goToNewBooking(): void {
    this.router.navigate(['/events', this.eventId, 'book'], this.returnDate ? { queryParams: { date: this.returnDate } } : {});
  }

  backToCalendar(): void {
    this.router.navigate(['/'], this.returnDate ? { queryParams: { date: this.returnDate } } : {});
  }
}
