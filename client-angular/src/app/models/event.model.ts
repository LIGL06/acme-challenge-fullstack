export interface Event {
  id: number;
  title: string;
  start: string; // ISO datetime string
  duration: number; // in minutes
  createdAt?: string;
  updatedAt?: string;
}

export interface Booking {
  id: number;
  firstName: string;
  lastName: string;
  eventId: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateBookingRequest {
  eventId: number;
  firstName: string;
  lastName: string;
}

