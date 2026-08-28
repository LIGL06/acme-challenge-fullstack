import { Routes } from '@angular/router';
import { CalendarComponent } from './components/calendar/calendar.component';
import { BookingFormComponent } from './components/booking-form/booking-form.component';
import { BookingManagementComponent } from './components/booking-management/booking-management.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';

export const routes: Routes = [
  { path: '', component: CalendarComponent },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'events/:id/manage', component: BookingManagementComponent },
  { path: 'events/:id/book', component: BookingFormComponent },
  { path: '**', redirectTo: '' }
];
