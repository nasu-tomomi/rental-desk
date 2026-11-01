package jp.rentaldesk.reservation;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Repository
public class ReservationRepository {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate template;

    public ReservationRepository(JdbcClient jdbc, NamedParameterJdbcTemplate template) {
        this.jdbc = jdbc;
        this.template = template;
    }

    public Optional<Price> findPrice(long id) {
        return jdbc.sql("SELECT * FROM price_master WHERE id = :id").param("id", id)
            .query((rs, n) -> new Price(rs.getLong("id"), rs.getString("division"),
                rs.getInt("price_1day"), rs.getInt("price_2day"), rs.getInt("price_after_3rd"))).optional();
    }

    public long insert(ReservationRequest r, int total) {
        var p = new MapSqlParameterSource()
            .addValue("source", r.source()).addValue("name", r.applicantName())
            .addValue("kana", r.applicantKana()).addValue("phone", r.mobilePhone())
            .addValue("contact", r.contactId()).addValue("email", r.email())
            .addValue("store", r.pickupStore()).addValue("date", r.rentalDate())
            .addValue("time", r.pickupTime()).addValue("returnAt", r.returnDatetime())
            .addValue("resort", r.skiResort()).addValue("transport", r.transport())
            .addValue("accommodation", r.accommodation()).addValue("payment", r.paymentMethod())
            .addValue("total", total).addValue("note", r.note());
        var key = new GeneratedKeyHolder();
        template.update("""
            INSERT INTO reservations (source, applicant_name, applicant_kana, mobile_phone,
                contact_id, email, pickup_store, rental_date, pickup_time, return_datetime,
                ski_resort, transport, accommodation, payment_method, total_amount, note, status)
            VALUES (:source, :name, :kana, :phone, :contact, :email, :store, :date, :time,
                :returnAt, :resort, :transport, :accommodation, :payment, :total, :note, '予約済')
            """, p, key, new String[]{"id"});
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    public void insertUser(long reservationId, ReservationRequest.User u, int amount) {
        var p = new MapSqlParameterSource().addValue("reservation", reservationId)
            .addValue("name", u.userName()).addValue("division", u.division())
            .addValue("days", u.rentalDays()).addValue("experience", u.experience())
            .addValue("height", u.height()).addValue("shoe", u.shoeSize())
            .addValue("price", u.priceMasterId()).addValue("small", u.smallItems())
            .addValue("memo", u.lentSizeMemo()).addValue("amount", amount);
        template.update("""
            INSERT INTO reservation_users (reservation_id, user_name, division, rental_days,
                experience, height, shoe_size, price_master_id, small_items, lent_size_memo, amount)
            VALUES (:reservation, :name, :division, :days, :experience, :height, :shoe,
                :price, :small, :memo, :amount)
            """, p);
    }

    public Optional<ReservationResponse> find(long id) {
        return jdbc.sql("SELECT * FROM reservations WHERE id = :id").param("id", id)
            .query((rs, n) -> mapReservation(rs, users(id))).optional();
    }

    public List<ReservationResponse> search(String keyword, LocalDate rentalDate) {
        String sql = "SELECT * FROM reservations WHERE 1=1";
        var params = new HashMap<String, Object>();
        if (keyword != null && !keyword.isBlank()) {
            // Treat %, _ and the escape character as literal search input.
            String escaped = keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            sql += " AND (applicant_name LIKE :keyword ESCAPE '!' OR applicant_kana LIKE :keyword ESCAPE '!')";
            params.put("keyword", "%" + escaped + "%");
        }
        if (rentalDate != null) {
            sql += " AND rental_date = :date";
            params.put("date", rentalDate);
        }
        sql += " ORDER BY rental_date ASC, pickup_time ASC, id ASC";
        // Fetch users once for the matching reservations, avoiding a query per reservation.
        var rows = jdbc.sql(sql).params(params).query((rs, n) -> mapReservation(rs, List.of())).list();
        if (rows.isEmpty()) return rows;
        var grouped = new HashMap<Long, List<ReservationResponse.User>>();
        jdbc.sql("""
            SELECT u.*, p.category AS price_category, p.item AS price_item
            FROM reservation_users u LEFT JOIN price_master p ON p.id = u.price_master_id
            WHERE u.reservation_id IN (:ids) ORDER BY u.id
            """)
            .param("ids", rows.stream().map(ReservationResponse::id).toList())
            .query((rs, n) -> Map.entry(rs.getLong("reservation_id"), mapUser(rs)))
            .list().forEach(e -> grouped.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return rows.stream().map(r -> new ReservationResponse(r.id(), r.source(), r.applicantName(),
            r.applicantKana(), r.mobilePhone(), r.contactId(), r.email(), r.pickupStore(), r.rentalDate(),
            r.pickupTime(), r.returnDatetime(), r.skiResort(), r.transport(), r.accommodation(),
            r.paymentMethod(), r.totalAmount(), r.note(), r.status(), r.createdAt(),
            grouped.getOrDefault(r.id(), List.of()))).toList();
    }

    private List<ReservationResponse.User> users(long id) {
        return jdbc.sql("""
            SELECT u.*, p.category AS price_category, p.item AS price_item
            FROM reservation_users u LEFT JOIN price_master p ON p.id = u.price_master_id
            WHERE u.reservation_id = :id ORDER BY u.id
            """)
            .param("id", id).query((rs, n) -> mapUser(rs)).list();
    }

    private ReservationResponse.User mapUser(ResultSet rs) throws SQLException {
        return new ReservationResponse.User(rs.getLong("id"), rs.getString("user_name"),
            rs.getString("division"), rs.getObject("rental_days", Integer.class), rs.getString("experience"),
            rs.getString("height"), rs.getString("shoe_size"), rs.getObject("price_master_id", Long.class),
            rs.getString("price_category"), rs.getString("price_item"),
            rs.getString("small_items"), rs.getString("lent_size_memo"), rs.getObject("amount", Integer.class));
    }

    private ReservationResponse mapReservation(ResultSet rs, List<ReservationResponse.User> users) throws SQLException {
        return new ReservationResponse(rs.getLong("id"), rs.getString("source"), rs.getString("applicant_name"),
            rs.getString("applicant_kana"), rs.getString("mobile_phone"), rs.getString("contact_id"),
            rs.getString("email"), rs.getString("pickup_store"), rs.getObject("rental_date", LocalDate.class),
            rs.getObject("pickup_time", LocalTime.class), rs.getObject("return_datetime", LocalDateTime.class),
            rs.getString("ski_resort"), rs.getString("transport"), rs.getString("accommodation"),
            rs.getString("payment_method"), rs.getInt("total_amount"), rs.getString("note"),
            rs.getString("status"), rs.getObject("created_at", LocalDateTime.class), users);
    }
}
