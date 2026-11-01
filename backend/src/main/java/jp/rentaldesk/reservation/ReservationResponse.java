package jp.rentaldesk.reservation;

import java.time.*;
import java.util.List;

public record ReservationResponse(
    long id, String source, String applicantName, String applicantKana,
    String mobilePhone, String contactId, String email, String pickupStore,
    LocalDate rentalDate, LocalTime pickupTime, LocalDateTime returnDatetime,
    String skiResort, String transport, String accommodation, String paymentMethod,
    int totalAmount, String note, String status, LocalDateTime createdAt,
    List<User> users
) {
    public record User(long id, String userName, String division, Integer rentalDays,
        String experience, String height, String shoeSize, Long priceMasterId,
        String priceCategory, String priceItem, String smallItems, String lentSizeMemo, Integer amount) {}
}
