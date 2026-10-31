package jp.rentaldesk.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record ReservationRequest(
    @NotBlank @Pattern(regexp = "net|walk-in") String source,
    @NotBlank @Size(max = 100) String applicantName,
    @Size(max = 100) String applicantKana,
    @Size(max = 30) String mobilePhone,
    @Size(max = 100) String contactId,
    @Email @Size(max = 255) String email,
    @Size(max = 50) String pickupStore,
    @NotNull LocalDate rentalDate,
    LocalTime pickupTime,
    LocalDateTime returnDatetime,
    @Size(max = 100) String skiResort,
    @Size(max = 50) String transport,
    @Size(max = 100) String accommodation,
    @Size(max = 20) String paymentMethod,
    @Size(max = 10000) String note,
    @NotEmpty @Size(max = 20) List<@NotNull @Valid User> users
) {
    @AssertTrue(message = "返却日時は貸出日時以降にしてください")
    public boolean isReturnDatetimeValid() {
        return rentalDate == null || returnDatetime == null ||
            !returnDatetime.isBefore(rentalDate.atTime(pickupTime == null ? LocalTime.MIN : pickupTime));
    }

    public record User(
        @Size(max = 100) String userName,
        @NotBlank @Pattern(regexp = "おとな|こども") String division,
        @NotNull @Min(1) @Max(365) Integer rentalDays,
        @Size(max = 50) String experience,
        @Size(max = 20) String height,
        @Size(max = 20) String shoeSize,
        @NotNull @Positive Long priceMasterId,
        @Size(max = 10000) String smallItems,
        @Size(max = 10000) String lentSizeMemo
    ) {}
}
