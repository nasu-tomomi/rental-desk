package jp.rentaldesk.reservation;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public class ReservationUserItemRepository {
    private final JdbcClient jdbc;

    public ReservationUserItemRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<ReservationUserItem> findByUser(long userId) {
        return jdbc.sql("SELECT * FROM reservation_user_items WHERE reservation_user_id = :id ORDER BY id")
            .param("id", userId).query((rs, n) -> new ReservationUserItem(rs.getLong("id"),
                rs.getLong("reservation_user_id"), rs.getLong("price_master_id"))).list();
    }

    public java.util.Map<Long, List<ReservationResponse.AdditionalItem>> findDetailsByReservations(List<Long> ids) {
        var grouped = new java.util.HashMap<Long, List<ReservationResponse.AdditionalItem>>();
        if (ids.isEmpty()) return grouped;
        jdbc.sql("""
            SELECT i.reservation_user_id, p.*, u.rental_days
            FROM reservation_user_items i
            JOIN reservation_users u ON u.id = i.reservation_user_id
            JOIN price_master p ON p.id = i.price_master_id
            WHERE u.reservation_id IN (:ids) ORDER BY i.id
            """).param("ids", ids).query((rs, n) -> {
                var price = new Price(rs.getLong("id"), rs.getString("division"), rs.getInt("price_1day"),
                    rs.getInt("price_2day"), rs.getInt("price_after_3rd"));
                return java.util.Map.entry(rs.getLong("reservation_user_id"),
                    new ReservationResponse.AdditionalItem(price.id(), rs.getString("category"),
                        rs.getString("item"), price.calculate(rs.getInt("rental_days"))));
            }).list().forEach(e -> grouped.computeIfAbsent(e.getKey(), k -> new java.util.ArrayList<>()).add(e.getValue()));
        return grouped;
    }

    @Transactional
    public void replace(long userId, List<Long> priceIds) {
        jdbc.sql("DELETE FROM reservation_user_items WHERE reservation_user_id = :id").param("id", userId).update();
        for (long priceId : priceIds) {
            jdbc.sql("INSERT INTO reservation_user_items (reservation_user_id, price_master_id) VALUES (:user, :price)")
                .param("user", userId).param("price", priceId).update();
        }
    }

    public void deleteByReservation(long reservationId) {
        jdbc.sql("""
            DELETE FROM reservation_user_items WHERE reservation_user_id IN
                (SELECT id FROM reservation_users WHERE reservation_id = :id)
            """).param("id", reservationId).update();
    }
}
