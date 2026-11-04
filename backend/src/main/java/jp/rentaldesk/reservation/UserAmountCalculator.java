package jp.rentaldesk.reservation;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class UserAmountCalculator {
    private final ReservationRepository repository;

    public UserAmountCalculator(ReservationRepository repository) { this.repository = repository; }

    public int calculate(long mainPriceId, String division, int days, List<Long> additionalPriceIds) {
        int amount = itemAmount(mainPriceId, division, days);
        for (long priceId : additionalPriceIds) {
            amount = Math.addExact(amount, itemAmount(priceId, division, days));
        }
        return amount;
    }

    private int itemAmount(long priceId, String division, int days) {
        var price = repository.findPrice(priceId)
            .orElseThrow(() -> new IllegalArgumentException("指定された料金マスタが存在しません"));
        if (!price.division().equals(division)) {
            throw new IllegalArgumentException("利用者の区分と料金マスタの区分が一致しません");
        }
        return price.calculate(days);
    }
}
