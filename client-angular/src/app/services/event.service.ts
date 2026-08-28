import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Event, Booking, CreateBookingRequest, BookingResponse } from '../models/event.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class EventService {
  private apiUrl = `${environment.apiUrl}/api`;

  constructor(private http: HttpClient) {}

  getEvents(): Observable<Event[]> {
    return this.http.get<Event[]>(`${this.apiUrl}/events`);
  }

  getEventById(id: number): Observable<Event> {
    return this.http.get<Event>(`${this.apiUrl}/events/${id}`);
  }

  getEventsByDate(date: string): Observable<Event[]> {
    return this.http.get<Event[]>(`${this.apiUrl}/events/date/${date}`);
  }

  getBookingsByEventId(eventId: number): Observable<Booking[]> {
    return this.http.get<Booking[]>(`${this.apiUrl}/bookings/event/${eventId}`);
  }

  createBooking(request: CreateBookingRequest): Observable<BookingResponse> {
    return this.http.post<BookingResponse>(`${this.apiUrl}/bookings`, request);
  }
}

