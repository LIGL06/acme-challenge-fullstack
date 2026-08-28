import { Routes } from '@angular/router';
import { CalendarComponent } from './components/calendar/calendar.component';
import { BookingFormComponent } from './components/booking-form/booking-form.component';

export const routes: Routes = [
  { path: '', component: CalendarComponent },
  { path: 'events/:id/book', component: BookingFormComponent },
  { path: '**', redirectTo: '' }
];
