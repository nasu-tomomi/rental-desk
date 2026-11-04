package jp.rentaldesk.reservation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ReservationService {
    private final ReservationRepository repository;
    private final ReservationUserItemRepository userItems;
    private final UserAmountCalculator calculator;

    public ReservationService(ReservationRepository repository, ReservationUserItemRepository userItems,
                              UserAmountCalculator calculator) {
        this.repository = repository;
        this.userItems = userItems;
        this.calculator = calculator;
    }

    @Transactional
    public ReservationResponse create(ReservationRequest request) {
        var amounts = new ArrayList<Integer>();
        int total = 0;
        for (var user : request.users()) {
            int amount = calculator.calculate(user.priceMasterId(), user.division(), user.rentalDays(), List.of());
            amounts.add(amount);
            total = Math.addExact(total, amount);
        }
        int maxDays = request.users().stream().mapToInt(ReservationRequest.User::rentalDays).max().orElseThrow();
        var returnDate = request.rentalDate().plusDays(maxDays - 1L);
        var returnTime = request.returnDatetime() == null ? java.time.LocalTime.MIDNIGHT : request.returnDatetime().toLocalTime();
        long id = repository.insert(request, total, returnDate.atTime(returnTime));
        for (int i = 0; i < request.users().size(); i++) {
            repository.insertUser(id, request.users().get(i), amounts.get(i));
        }
        return get(id);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(long id) {
        return repository.find(id)
            .orElseThrow(() -> new NoSuchElementException("指定された予約が存在しません"));
    }

    @Transactional
    public void delete(long id) {
        if (!repository.lockReservation(id)) {
            throw new NoSuchElementException("指定された予約が存在しません");
        }
        repository.delete(id);
    }

    @Transactional
    public ReservationResponse updateStatus(long id, String status) {
        var current = get(id);
        String next = switch (current.status()) {
            case "予約済" -> "受付済";
            case "受付済" -> "貸出中";
            case "貸出中" -> "返却済";
            default -> null;
        };
        boolean canCancel = "キャンセル".equals(status)
            && ("予約済".equals(current.status()) || "受付済".equals(current.status()));
        if (!canCancel && (next == null || !next.equals(status))) {
            throw new IllegalArgumentException("指定されたステータスへの変更はできません");
        }
        if (!repository.updateStatus(id, current.status(), status)) {
            throw new IllegalArgumentException("予約のステータスが変更されています。再取得してください");
        }
        return get(id);
    }

    @Transactional
    public ReservationResponse updateUsers(long id, ReservationUsersRequest request) {
        // Serialize edits to a reservation so its users and total stay consistent.
        if (!repository.lockReservation(id)) {
            throw new NoSuchElementException("指定された予約が存在しません");
        }
        var current = get(id);
        var amounts = new java.util.HashMap<Long, Integer>();
        for (var user : request.users()) {
            if (amounts.containsKey(user.id())) {
                throw new IllegalArgumentException("利用者IDが重複しています");
            }
            var existing = current.users().stream().filter(u -> u.id() == user.id()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("指定された利用者が予約に存在しません"));
            var additionalPriceIds = userItems.findByUser(user.id()).stream()
                .map(ReservationUserItem::priceMasterId).toList();
            amounts.put(user.id(), calculator.calculate(user.priceMasterId(), existing.division(),
                user.rentalDays(), additionalPriceIds));
        }
        int total = 0;
        for (var user : current.users()) {
            total = Math.addExact(total, amounts.getOrDefault(user.id(), user.amount()));
        }
        for (var user : request.users()) {
            repository.updateUser(id, user, amounts.get(user.id()));
        }
        repository.updateTotal(id, total);
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> search(String keyword, LocalDate rentalDate) {
        return repository.search(keyword, rentalDate);
    }
}
