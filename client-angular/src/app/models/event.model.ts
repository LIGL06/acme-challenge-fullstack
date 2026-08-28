export type AvailabilityStatus = 'AVAILABLE' | 'LIMITED' | 'FULL';

export interface Event {
  id: number;
  title: string;
  start: string; // ISO datetime string
  duration: number; // in minutes
  capacity: number;
  pricePerPerson: number;
  currentBookings: number;
  availableSeats: number;
  availabilityStatus: AvailabilityStatus;
  createdAt?: string;
  updatedAt?: string;
}

export type BookingStatus = 'CONFIRMED' | 'CANCELLED' | 'WAITLISTED';

export interface Booking {
  id: number;
  firstName: string;
  lastName: string;
  eventId: number;
  customerEmail: string;
  participantCount: number;
  notes?: string;
  status: BookingStatus;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateBookingRequest {
  eventId: number;
  firstName: string;
  lastName: string;
  customerEmail: string;
  participantCount: number;
  notes?: string;
}

export interface BookingResponse {
  id: number;
  eventId: number;
  firstName: string;
  lastName: string;
  customerEmail: string;
  participantCount: number;
  notes?: string;
  status: BookingStatus;
  message: string;
  createdAt: string;
}

