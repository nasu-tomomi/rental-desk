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
            .andExpect(jsonPath("$.users[0].priceCategory").value("スタンダード"))
            .andExpect(jsonPath("$.users[0].priceItem").value("スキー OR スノーボード セット"))
            .andExpect(jsonPath("$.users[0].amount").value(10000))
            .andExpect(jsonPath("$.users[1].amount").value(3000));
        assertThat(count("reservations")).isEqualTo(1);
        assertThat(count("reservation_users")).isEqualTo(2);
    }

    @Test void getsReservationWithUsersAndPriceDetails() throws Exception {
        var created = service.create(mapper.readValue(REQUEST, ReservationRequest.class));
        mvc.perform(get("/api/reservations/{id}", created.id()))
            .andExpect(status().isOk())
            .andExpect(content().json(mapper.writeValueAsString(created)))
            .andExpect(jsonPath("$.id").value(created.id()))
            .andExpect(jsonPath("$.applicantName").value("山田 Alice"))
            .andExpect(jsonPath("$.users.length()").value(2))
            .andExpect(jsonPath("$.users[0].userName").value("Alice"))
            .andExpect(jsonPath("$.users[0].priceMasterId").value(1))
            .andExpect(jsonPath("$.users[0].priceCategory").value("スタンダード"))
            .andExpect(jsonPath("$.users[0].priceItem").value("スキー OR スノーボード セット"))
            .andExpect(jsonPath("$.users[1].userName").value("Bob"))
            .andExpect(jsonPath("$.users[1].priceMasterId").value(2))
            .andExpect(jsonPath("$.users[1].priceCategory").value("スタンダード"))
            .andExpect(jsonPath("$.users[1].priceItem").value("スキー OR スノーボード セット"));
    }

    @Test void returnsNotFoundForMissingReservation() throws Exception {
        service.create(mapper.readValue(REQUEST, ReservationRequest.class));
        mvc.perform(get("/api/reservations/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("指定された予約が存在しません"));
    }

    @Test void advancesStatusAndReturnsGetDetail() throws Exception {
        var created = service.create(mapper.readValue(REQUEST, ReservationRequest.class));
        for (String next : new String[]{"受付済", "貸出中", "返却済"}) {
            var result = mvc.perform(patch("/api/reservations/{id}/status", created.id())
                    .contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("status", next))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(next))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            var expected = mapper.readTree(mapper.writeValueAsString(created));
            ((com.fasterxml.jackson.databind.node.ObjectNode) expected).put("status", next);
            assertThat(mapper.readTree(result)).isEqualTo(expected);
            mvc.perform(get("/api/reservations/{id}", created.id()))
                .andExpect(status().isOk()).andExpect(content().json(result));
        }
    }

    @Test void rejectsAllOtherTransitionsAndInvalidBodiesWithoutChanges() throws Exception {
        var created = service.create(mapper.readValue(REQUEST, ReservationRequest.class));
        String[] statuses = {"予約済", "受付済", "貸出中", "返却済"};
        for (int i = 0; i < statuses.length; i++) {
            jdbc.update("UPDATE reservations SET status = ? WHERE id = ?", statuses[i], created.id());
            var before = service.get(created.id());
            var bodies = new java.util.ArrayList<String>(java.util.List.of(
                "{}", "{\"status\":null}", "{\"status\":\"\"}", "{\"status\":\" \"}",
                "{\"status\":\"不明\"}", "{", "null"));
            for (int j = 0; j < statuses.length; j++) {
                if (j != i + 1) bodies.add(mapper.writeValueAsString(java.util.Map.of("status", statuses[j])));
            }
            for (String body : bodies) {
                mvc.perform(patch("/api/reservations/{id}/status", created.id())
                        .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(400));
                assertThat(service.get(created.id())).isEqualTo(before);
            }
        }
    }

    @Test void returnsProblemDetailForMissingStatusUpdateId() throws Exception {
        mvc.perform(patch("/api/reservations/{id}/status", Long.MAX_VALUE)
                .contentType("application/json").content("{\"status\":\"受付済\"}"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("指定された予約が存在しません"));
    }

    @Test void rejectsConcurrentStatusChange() throws Exception {
        var created = service.create(mapper.readValue(REQUEST, ReservationRequest.class));
        doReturn(false).when(repository).updateStatus(created.id(), "予約済", "受付済");
        mvc.perform(patch("/api/reservations/{id}/status", created.id())
                .contentType("application/json").content("{\"status\":\"受付済\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        assertThat(service.get(created.id())).isEqualTo(created);
    }

    @Test void conditionalUpdateDoesNotOverwriteChangedStatus() throws Exception {
        var created = service.create(mapper.readValue(REQUEST, ReservationRequest.class));
        assertThat(repository.updateStatus(created.id(), "予約済", "受付済")).isTrue();
        assertThat(repository.updateStatus(created.id(), "予約済", "受付済")).isFalse();
        assertThat(service.get(created.id()).status()).isEqualTo("受付済");
    }

    @Test void listsAndFiltersByNameKanaRomanLettersAndDate() throws Exception {
        mvc.perform(get("/api/reservations")).andExpect(status().isOk()).andExpect(content().json("[]"));
        service.create(mapper.readValue(REQUEST, ReservationRequest.class));
        for (String keyword : new String[]{"山田", "ヤマ", "Alice"}) {
            mvc.perform(get("/api/reservations").param("keyword", keyword).param("rentalDate", "2026-11-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].users.length()").value(2))
                .andExpect(jsonPath("$[0].users[0].priceMasterId").value(1))
                .andExpect(jsonPath("$[0].users[0].priceCategory").value("スタンダード"))
                .andExpect(jsonPath("$[0].users[0].priceItem").value("スキー OR スノーボード セット"));
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

    @Test void listsAllPricesAndSupportsEmptyMaster() throws Exception {
        var result = mvc.perform(get("/api/prices").header("Origin", "http://localhost:5173"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var rows = mapper.readTree(result);
        assertThat(rows.size()).isEqualTo(count("price_master"));
        var expected = jdbc.queryForList("SELECT * FROM price_master ORDER BY id");
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            var db = expected.get(i);
            assertThat(row.get("id").asLong()).isEqualTo(((Number) db.get("id")).longValue());
            for (String field : new String[]{"division", "category", "item"}) {
                assertThat(row.get(field).asText()).isEqualTo(db.get(field));
            }
            assertThat(row.get("price1day").asInt()).isEqualTo(db.get("price_1day"));
            assertThat(row.get("price2day").asInt()).isEqualTo(db.get("price_2day"));
            assertThat(row.get("priceAfter3rd").asInt()).isEqualTo(db.get("price_after_3rd"));
        }
        jdbc.update("DELETE FROM price_master");
        mvc.perform(get("/api/prices")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    private String userEdit(long userId, long priceId, int days, String memo) throws Exception {
        return mapper.writeValueAsString(new ReservationUsersRequest.User(userId, priceId, days, memo));
    }

    @Test void updatesItemsDaysAndNullableMemoWithCalculatedTotals() throws Exception {
        var created = service.create(mapper.readValue(REQUEST, ReservationRequest.class));
        long alice = created.users().get(0).id(), bob = created.users().get(1).id();
        for (int days : new int[]{1, 2, 5}) {
            int adultAmount = days == 1 ? 6000 : 10500 + (days - 2) * 4500;
            String body = "{\"users\":[" + userEdit(alice, 3, days, "ブーツ26cm") + ","
                + userEdit(bob, 4, 2, null) + "]}";
            var result = mvc.perform(patch("/api/reservations/{id}/users", created.id())
                    .contentType("application/json").content(body)
                    .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(jsonPath("$.totalAmount").value(adultAmount + 7500))
                .andExpect(jsonPath("$.users[0].amount").value(adultAmount))
                .andExpect(jsonPath("$.users[0].rentalDays").value(days))
                .andExpect(jsonPath("$.users[0].priceMasterId").value(3))
                .andExpect(jsonPath("$.users[0].priceItem").value("スキー OR スノーボード フルセット"))
                .andExpect(jsonPath("$.users[0].lentSizeMemo").value("ブーツ26cm"))
                .andExpect(jsonPath("$.users[1].lentSizeMemo").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            mvc.perform(get("/api/reservations/{id}", created.id()))
                .andExpect(status().isOk()).andExpect(content().json(result));
        }
        mvc.perform(patch("/api/reservations/{id}/users", created.id())
                .contentType("application/json").content("{\"users\":[" + userEdit(alice, 1, 1, null) + "]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(11500))
            .andExpect(jsonPath("$.users[0].lentSizeMemo").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.users[1].amount").value(7500));
        assertThat(service.get(created.id()).users().get(0).userName()).isEqualTo("Alice");
        assertThat(count("reservation_users")).isEqualTo(2);
    }

    @Test void rejectsInvalidUserEditsAndKeepsAllData() throws Exception {
        var created = service.create(mapper.readValue(REQUEST, ReservationRequest.class));
        var other = service.create(mapper.readValue(REQUEST, ReservationRequest.class));
        long alice = created.users().get(0).id(), bob = created.users().get(1).id();
        var bodies = new java.util.ArrayList<String>(java.util.List.of(
            "{}", "{\"users\":null}", "{\"users\":[]}", "{\"users\":[null]}", "{\"users\":[{}]}",
            "{\"users\":[{\"id\":" + alice + ",\"priceMasterId\":1,\"rentalDays\":null}]}"));
        for (String invalid : new String[]{userEdit(99999, 1, 1, null),
                userEdit(other.users().get(0).id(), 1, 1, null), userEdit(bob, 99999, 1, null),
                userEdit(bob, 1, 1, null), userEdit(bob, 2, 0, null), userEdit(bob, 2, 366, null),
                userEdit(bob, 0, 1, null), userEdit(bob, 2, 1, "x".repeat(10001)),
                userEdit(alice, 1, 1, null)}) {
            bodies.add("{\"users\":[" + userEdit(alice, 3, 2, "変更") + "," + invalid + "]}");
        }
        for (String body : bodies) {
            mvc.perform(patch("/api/reservations/{id}/users", created.id())
                    .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
            assertThat(service.get(created.id())).isEqualTo(created);
            assertThat(service.get(other.id())).isEqualTo(other);
        }
        mvc.perform(patch("/api/reservations/{id}/users", Long.MAX_VALUE)
                .contentType("application/json").content("{\"users\":[" + userEdit(alice, 1, 1, null) + "]}"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.detail").value("指定された予約が存在しません"));
    }

    @Test void rollsBackUserEditsWhenSavingTotalFails() throws Exception {
        var created = service.create(mapper.readValue(REQUEST, ReservationRequest.class));
        var request = new ReservationUsersRequest(java.util.List.of(
            new ReservationUsersRequest.User(created.users().get(0).id(), 3L, 2, "変更"),
            new ReservationUsersRequest.User(created.users().get(1).id(), 4L, 2, null)));
        doThrow(new IllegalStateException("simulated total save failure"))
            .when(repository).updateTotal(anyLong(), anyInt());
        assertThatThrownBy(() -> service.updateUsers(created.id(), request)).isInstanceOf(IllegalStateException.class);
        assertThat(service.get(created.id())).isEqualTo(created);
    }

    @Test void rejectsAmountAndTotalOverflowWithoutSaving() throws Exception {
        var created = service.create(mapper.readValue(REQUEST, ReservationRequest.class));
        for (int days : new int[]{1, 3}) {
            jdbc.update("UPDATE price_master SET price_1day = ?, price_2day = ?, price_after_3rd = ? WHERE id = 1",
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
            mvc.perform(patch("/api/reservations/{id}/users", created.id())
                    .contentType("application/json").content("{\"users\":[" + userEdit(created.users().get(0).id(), 1, days, null) + "]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
            assertThat(service.get(created.id())).isEqualTo(created);
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
