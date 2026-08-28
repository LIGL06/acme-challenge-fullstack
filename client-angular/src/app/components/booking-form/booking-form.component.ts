import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { EventService } from '../../services/event.service';
import { BookingResponse, CreateBookingRequest, Event } from '../../models/event.model';

function notBlankValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    return typeof value === 'string' && value.trim().length === 0 ? { notBlank: true } : null;
  };
}

function integerValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    return value === null || value === '' || Number.isInteger(value) ? null : { integer: true };
  };
}

@Component({
  selector: 'app-booking-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './booking-form.component.html',
  styleUrl: './booking-form.component.scss'
})
export class BookingFormComponent implements OnInit {
  event: Event | null = null;
  loadingEvent = true;
  loadError: string | null = null;
  submitting = false;
  submitError: string | null = null;
  result: BookingResponse | null = null;
  returnDate: string | null = null;

  form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, notBlankValidator()]],
    lastName: ['', [Validators.required, notBlankValidator()]],
    customerEmail: ['', [Validators.required, Validators.email]],
    participantCount: [1, [Validators.required, Validators.min(1), integerValidator()]],
    notes: ['']
  });

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private eventService: EventService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const id = Number(params.get('id'));

      this.returnDate = this.route.snapshot.queryParamMap.get('date');
      this.event = null;
      this.result = null;
      this.submitError = null;
      this.loadError = null;
      this.form.enable();
      this.form.reset({ participantCount: 1 });

      if (!Number.isFinite(id) || id <= 0) {
        this.loadingEvent = false;
        this.loadError = 'Invalid event.';
        return;
      }

      this.loadingEvent = true;
      this.eventService.getEventById(id).subscribe({
        next: (event) => {
          this.event = event;
          this.loadingEvent = false;
        },
        error: (err: HttpErrorResponse) => {
          this.loadingEvent = false;
          this.loadError = err.status === 404
            ? 'This event could not be found. It may have been removed.'
            : 'Could not load this event. Please try again.';
        }
      });
    });
  }

  submit(): void {
    this.submitError = null;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (!this.event) {
      return;
    }

    this.submitting = true;
    this.form.disable();

    const request: CreateBookingRequest = { eventId: this.event.id, ...this.form.getRawValue() };

    this.eventService.createBooking(request).subscribe({
      next: (response) => {
        this.result = response;
        this.submitting = false;
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        this.form.enable();

        if (err.status === 400) {
          this.submitError = 'There was a problem with the booking details. Please check the form and try again.';
        } else if (err.status === 404) {
          this.submitError = 'This event could not be found. It may have been removed.';
        } else {
          this.submitError = 'Something went wrong creating the booking. Please try again.';
        }
      }
    });
  }

  backToCalendar(): void {
    this.router.navigate(['/'], this.returnDate ? { queryParams: { date: this.returnDate } } : {});
  }

  backToManagement(): void {
    if (!this.event) {
      return;
    }
    this.router.navigate(['/events', this.event.id, 'manage'], this.returnDate ? { queryParams: { date: this.returnDate } } : {});
  }

  cancel(): void {
    this.backToManagement();
  }
}
