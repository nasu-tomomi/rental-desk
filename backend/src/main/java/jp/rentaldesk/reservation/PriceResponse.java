package jp.rentaldesk.reservation;

public record PriceResponse(long id, String division, String category, String item,
    int price1day, int price2day, int priceAfter3rd) {}
