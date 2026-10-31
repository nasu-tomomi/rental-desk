package jp.rentaldesk;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.rentaldesk.reservation.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:reservation;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
class ReservationApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ReservationService service;
    @MockitoSpyBean ReservationRepository repository;

    private static final String REQUEST = """
        {"source":"net","applicantName":"山田 Alice","applicantKana":"ヤマダ",
         "rentalDate":"2026-11-01","pickupTime":"09:00:00",
         "returnDatetime":"2026-11-03T17:00:00",
         "users":[
           {"userName":"Alice","division":"おとな","rentalDays":3,"priceMasterId":1},
           {"userName":"Bob","division":"こども","rentalDays":1,"priceMasterId":2}
         ]}
        """;

    @BeforeEach
    void setup() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        // Use the actual project schema/seed, adapting only MySQL-specific DDL for H2.
        String schema = new ClassPathResource("db/01-schema.sql").getContentAsString(StandardCharsets.UTF_8)
            .replaceAll("(?m)^--.*$", "")
            .replace("SET NAMES utf8mb4;", "")
            .replace(" COLLATE utf8mb4_unicode_ci", "")
            .replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        executeStatements(schema);
        executeStatements(new ClassPathResource("db/02-seed-price-master.sql")
            .getContentAsString(StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "")
            .replace("SET NAMES utf8mb4;", ""));
    }

    private void executeStatements(String sql) {
        for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
    }

    @Test void createsReservationAndUsersWithCalculatedTotal() throws Exception {
        mvc.perform(post("/api/reservations").contentType("application/json").content(REQUEST))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("予約済"))
            .andExpect(jsonPath("$.totalAmount").value(13000))
            .andExpect(jsonPath("$.users.length()").value(2))
            .andExpect(jsonPath("$.users[0].amount").value(10000))
            .andExpect(jsonPath("$.users[1].amount").value(3000));
        assertThat(count("reservations")).isEqualTo(1);
        assertThat(count("reservation_users")).isEqualTo(2);
    }

    @Test void listsAndFiltersByNameKanaRomanLettersAndDate() throws Exception {
        mvc.perform(get("/api/reservations")).andExpect(status().isOk()).andExpect(content().json("[]"));
        service.create(mapper.readValue(REQUEST, ReservationRequest.class));
        for (String keyword : new String[]{"山田", "ヤマ", "Alice"}) {
            mvc.perform(get("/api/reservations").param("keyword", keyword).param("rentalDate", "2026-11-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].users.length()").value(2));
        }
        mvc.perform(get("/api/reservations").param("rentalDate", "2026-11-02"))
            .andExpect(content().json("[]"));
        for (String literal : new String[]{"%", "_", "' OR 1=1 --"}) {
            mvc.perform(get("/api/reservations").param("keyword", literal))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        }
    }

    @Test void rejectsInvalidInputWithoutSaving() throws Exception {
        for (String input : new String[]{"{}", REQUEST.replace("山田 Alice", " "),
            REQUEST.replace("\"rentalDays\":3", "\"rentalDays\":0"),
            REQUEST.replace("2026-11-03T17:00:00", "2026-10-31T17:00:00"),
            REQUEST.replace("\"source\":\"net\"", "\"source\":\"other\"")}) {
            mvc.perform(post("/api/reservations").contentType("application/json").content(input))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/reservations").param("rentalDate", "bad-date"))
            .andExpect(status().isBadRequest());
        assertThat(count("reservations")).isZero();
    }

    @Test void rejectsMissingPriceAndDivisionMismatch() throws Exception {
        for (String input : new String[]{REQUEST.replace("\"priceMasterId\":1", "\"priceMasterId\":9999"),
            REQUEST.replace("\"priceMasterId\":2", "\"priceMasterId\":1")}) {
            mvc.perform(post("/api/reservations").contentType("application/json").content(input))
                .andExpect(status().isBadRequest());
        }
        assertThat(count("reservations")).isZero();
        assertThat(count("reservation_users")).isZero();
    }

    @Test void rollsBackWholeReservationIfSecondUserSaveFails() throws Exception {
        var request = mapper.readValue(REQUEST, ReservationRequest.class);
        doAnswer(invocation -> {
            if (invocation.getArgument(1, ReservationRequest.User.class).userName().equals("Bob"))
                throw new IllegalStateException("simulated second user failure");
            return invocation.callRealMethod();
        }).when(repository).insertUser(anyLong(), any(), anyInt());
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(IllegalStateException.class);
        assertThat(count("reservations")).isZero();
        assertThat(count("reservation_users")).isZero();
    }

    @Test void calculatesOneTwoAndAdditionalDays() {
        var price = new Price(1, "おとな", 4000, 7000, 3000);
        assertThat(price.calculate(1)).isEqualTo(4000);
        assertThat(price.calculate(2)).isEqualTo(7000);
        assertThat(price.calculate(3)).isEqualTo(10000);
        assertThat(price.calculate(5)).isEqualTo(16000);
        assertThatThrownBy(() -> price.calculate(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
