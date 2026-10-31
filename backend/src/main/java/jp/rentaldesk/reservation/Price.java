package jp.rentaldesk.reservation;

public record Price(long id, String division, int price1day, int price2day, int priceAfter3rd) {
    public int calculate(int days) {
        if (days < 1 || days > 365) throw new IllegalArgumentException("利用日数は1〜365日で指定してください");
        return days == 1 ? price1day : Math.addExact(price2day, Math.multiplyExact(days - 2, priceAfter3rd));
    }
}
