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

export interface DashboardEventSummary {
  id: number;
  title: string;
  start: string; // ISO datetime string
  capacity: number;
  currentBookings: number;
  availableSeats: number;
  availabilityStatus: AvailabilityStatus;
  waitlistedGuests: number; // sum of participantCount across this event's WAITLISTED bookings
}

export interface DashboardResponse {
  date: string; // ISO date string
  totalEvents: number;
  totalConfirmedParticipants: number;
  totalAvailableSeats: number;
  // Row COUNT of WAITLISTED bookings across today's events -- NOT a sum of
  // participantCount. See DashboardEventSummary.waitlistedGuests for the
  // per-event guest-sum figure, which is intentionally a different kind of number.
  totalWaitlistedBookings: number;
  events: DashboardEventSummary[];
}

