import { Routes } from '@angular/router';
import { CalendarComponent } from './components/calendar/calendar.component';
import { BookingFormComponent } from './components/booking-form/booking-form.component';
import { BookingManagementComponent } from './components/booking-management/booking-management.component';

export const routes: Routes = [
  { path: '', component: CalendarComponent },
  { path: 'events/:id/manage', component: BookingManagementComponent },
  { path: 'events/:id/book', component: BookingFormComponent },
  { path: '**', redirectTo: '' }
];
