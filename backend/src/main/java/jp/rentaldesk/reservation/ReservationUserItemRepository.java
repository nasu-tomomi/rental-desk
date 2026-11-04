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
