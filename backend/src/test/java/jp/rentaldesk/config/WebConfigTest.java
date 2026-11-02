package jp.rentaldesk.config;

import jp.rentaldesk.reservation.ReservationController;
import jp.rentaldesk.reservation.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReservationController.class)
@Import(WebConfig.class)
class WebConfigTest {
    @Autowired MockMvc mvc;
    @MockitoBean ReservationService service;

    @ParameterizedTest
    @ValueSource(strings = {"/api/reservations", "/api/reservations/3"})
    void allowsFrontendGet(String path) throws Exception {
        mvc.perform(get(path).header("Origin", "http://localhost:5173"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/reservations", "/api/reservations/3"})
    void rejectsOtherOrigins(String path) throws Exception {
        mvc.perform(get(path).header("Origin", "http://example.com"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void allowsFrontendPatchPreflightAndRequest() throws Exception {
        mvc.perform(options("/api/reservations/3/status")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(header().string("Access-Control-Allow-Methods", "GET,PATCH"))
            .andExpect(header().string("Access-Control-Allow-Headers", "content-type"));
        mvc.perform(patch("/api/reservations/3/status")
                .header("Origin", "http://localhost:5173")
                .contentType("application/json").content("{\"status\":\"受付済\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/reservations", "/api/reservations/3", "/api/reservations/3/status"})
    void allowsGetPreflightButRejectsPost(String path) throws Exception {
        mvc.perform(options(path).header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Methods", "GET,PATCH"));
        mvc.perform(options(path).header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden());
    }
}
