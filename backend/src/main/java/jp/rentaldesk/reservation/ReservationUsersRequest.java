package jp.rentaldesk.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ReservationUsersRequest(
    @NotEmpty @Size(max = 20) List<@NotNull @Valid User> users
) {
    public record User(
        @NotNull @Positive Long id,
        @NotNull @Positive Long priceMasterId,
        @NotNull @Min(1) @Max(365) Integer rentalDays,
        @Size(max = 10000) String lentSizeMemo
    ) {}
}
