package jp.rentaldesk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:auth;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
class StaffAuthApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private static final String LOGIN = "{\"username\":\"staff\",\"password\":\"password1234\"}";

    @BeforeEach
    void setup() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        for (String file : new String[]{"01-schema.sql", "02-seed-price-master.sql", "06-seed-staff.sql"}) {
            String sql = new ClassPathResource("db/" + file).getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("(?m)^--.*$", "").replace("SET NAMES utf8mb4;", "")
                .replace(" COLLATE utf8mb4_unicode_ci", "")
                .replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
            for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/reservations", "/api/reservations/1", "/api/prices", "/api/auth/me"})
    void rejectsUnauthenticatedApisWithJsonInsteadOfRedirect(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.message").isString())
            .andExpect(header().doesNotExist("Location"));
    }

    @Test void logsInPersistsSessionAndAllowsApis() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType("application/json").content(LOGIN))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("staff"))
            .andExpect(jsonPath("$.password").doesNotExist()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("staff"));
        mvc.perform(get("/api/reservations").session(session)).andExpect(status().isOk())
            .andExpect(content().json("[]"));
        mvc.perform(get("/api/prices").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(38));
        String hash = jdbc.queryForObject("SELECT password FROM staff WHERE username = 'staff'", String.class);
        assertThat(hash).isNotEqualTo("password1234").startsWith("$2");
        assertThat(encoder.matches("password1234", hash)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"username\":\"staff\",\"password\":\"wrong\"}",
                           "{\"username\":\"unknown\",\"password\":\"password1234\"}"})
    void rejectsWrongCredentialsWithoutCreatingSession(String body) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType("application/json").content(body))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").isString()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void logoutInvalidatesSessionAndRequiresLoginAgain() throws Exception {
        var session = (MockHttpSession) mvc.perform(post("/api/auth/login").contentType("application/json")
            .content(LOGIN)).andReturn().getRequest().getSession(false);
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isOk())
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/reservations").cookie(new jakarta.servlet.http.Cookie("JSESSIONID", session.getId())))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test void rotatesExistingSessionIdOnLogin() throws Exception {
        var session = new MockHttpSession();
        String oldId = session.getId();
        mvc.perform(post("/api/auth/login").session(session).contentType("application/json").content(LOGIN))
            .andExpect(status().isOk());
        assertThat(session.getId()).isNotEqualTo(oldId);
    }

    @Test void allowsCredentialedFrontendCorsIncludingUnauthenticatedResponse() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:5173")
                .contentType("application/json").content(LOGIN))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(get("/api/reservations").header("Origin", "http://localhost:5173"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test void rejectsUntrustedCorsOrigin() throws Exception {
        mvc.perform(post("/api/auth/login").header("Origin", "http://example.com")
                .contentType("application/json").content(LOGIN)).andExpect(status().isForbidden());
    }
}
