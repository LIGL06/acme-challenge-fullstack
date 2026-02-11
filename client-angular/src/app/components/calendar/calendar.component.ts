import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EventService } from '../../services/event.service';
import { Event } from '../../models/event.model';

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './calendar.component.html',
  styleUrl: './calendar.component.scss'
})
export class CalendarComponent implements OnInit {
  events: Event[] = [];
  selectedDate: Date = new Date();
  loading = false;
  error: string | null = null;

  // Hours to display in the calendar (8 AM to 8 PM)
  hours: number[] = Array.from({ length: 13 }, (_, i) => i + 8);

  constructor(private eventService: EventService) {}

  ngOnInit(): void {
    this.loadEvents();
  }

  loadEvents(): void {
    this.loading = true;
    this.error = null;

    const dateStr = this.formatDate(this.selectedDate);
    this.eventService.getEventsByDate(dateStr).subscribe({
      next: (events) => {
        this.events = events;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load events. Make sure the server is running.';
        this.loading = false;
        console.error('Error loading events:', err);
      }
    });
  }

  previousDay(): void {
    this.selectedDate = new Date(this.selectedDate.getTime() - 24 * 60 * 60 * 1000);
    this.loadEvents();
  }

  nextDay(): void {
    this.selectedDate = new Date(this.selectedDate.getTime() + 24 * 60 * 60 * 1000);
    this.loadEvents();
  }

  goToToday(): void {
    this.selectedDate = new Date();
    this.loadEvents();
  }

  formatDate(date: Date): string {
    return date.toISOString().split('T')[0];
  }

  formatDisplayDate(date: Date): string {
    return date.toLocaleDateString('en-US', {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  }

  formatHour(hour: number): string {
    const suffix = hour >= 12 ? 'PM' : 'AM';
    const displayHour = hour > 12 ? hour - 12 : hour;
    return `${displayHour}:00 ${suffix}`;
  }

  getEventPosition(event: Event): { top: string; height: string } {
    const startTime = new Date(event.start);
    const startHour = startTime.getHours() + startTime.getMinutes() / 60;
    const top = (startHour - 8) * 60; // 60px per hour, starting at 8 AM
    const height = event.duration;
    return { top: `${top}px`, height: `${height}px` };
  }

  formatEventTime(event: Event): string {
    const start = new Date(event.start);
    return start.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
  }

  // Candidate TODO: Implement onEventClick to show booking form
  onEventClick(event: Event): void {
    console.log('Event clicked:', event);
    // TODO: Open a dialog/modal to create a booking for this event
  }
}

