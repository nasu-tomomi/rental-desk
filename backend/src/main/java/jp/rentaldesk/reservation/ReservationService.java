package jp.rentaldesk.reservation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReservationService {
    private final ReservationRepository repository;
    public ReservationService(ReservationRepository repository) { this.repository = repository; }

    @Transactional
    public ReservationResponse create(ReservationRequest request) {
        var amounts = new ArrayList<Integer>();
        int total = 0;
        for (var user : request.users()) {
            var price = repository.findPrice(user.priceMasterId())
                .orElseThrow(() -> new IllegalArgumentException("指定された料金マスタが存在しません"));
            if (!price.division().equals(user.division())) {
                throw new IllegalArgumentException("利用者の区分と料金マスタの区分が一致しません");
            }
            int amount = price.calculate(user.rentalDays());
            amounts.add(amount);
            total = Math.addExact(total, amount);
        }
        long id = repository.insert(request, total);
        for (int i = 0; i < request.users().size(); i++) {
            repository.insertUser(id, request.users().get(i), amounts.get(i));
        }
        return repository.find(id);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> search(String keyword, LocalDate rentalDate) {
        return repository.search(keyword, rentalDate);
    }
}
