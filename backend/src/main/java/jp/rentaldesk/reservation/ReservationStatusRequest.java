package jp.rentaldesk.reservation;

import jakarta.validation.constraints.NotBlank;

public record ReservationStatusRequest(@NotBlank String status) {}
