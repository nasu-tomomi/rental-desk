package jp.rentaldesk.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class StaffRepository {
    private final JdbcClient jdbc;
    public StaffRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<Staff> findByUsername(String username) {
        return jdbc.sql("SELECT id, username, password FROM staff WHERE username = :username")
            .param("username", username).query((rs, n) -> new Staff(rs.getLong("id"),
                rs.getString("username"), rs.getString("password"))).optional();
    }
}
