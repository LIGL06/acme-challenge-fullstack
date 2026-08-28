import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { EventService } from '../../services/event.service';
import { DashboardEventSummary, DashboardResponse } from '../../models/event.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  dashboard: DashboardResponse | null = null;
  loading = false;
  error: string | null = null;

  constructor(
    private eventService: EventService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadDashboard();
  }

  loadDashboard(): void {
    this.loading = true;
    this.error = null;

    this.eventService.getDashboard().subscribe({
      next: (dashboard) => {
        this.dashboard = dashboard;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load dashboard. Make sure the server is running.';
        this.loading = false;
        console.error('Error loading dashboard:', err);
      }
    });
  }

  availabilityLabel(event: DashboardEventSummary): string {
    switch (event.availabilityStatus) {
      case 'FULL':
        return 'Full';
      case 'LIMITED':
        return 'Limited';
      default:
        return 'Available';
    }
  }

  onEventClick(event: DashboardEventSummary): void {
    this.router.navigate(['/events', event.id, 'manage']);
  }

  backToCalendar(): void {
    this.router.navigate(['/']);
  }
}
