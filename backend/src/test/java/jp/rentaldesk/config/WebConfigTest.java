package jp.rentaldesk.config;

import jp.rentaldesk.reservation.ReservationController;
import jp.rentaldesk.reservation.ReservationService;
import org.junit.jupiter.api.Test;
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

    @Test void allowsFrontendGet() throws Exception {
        mvc.perform(get("/api/reservations").header("Origin", "http://localhost:5173"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test void rejectsOtherOrigins() throws Exception {
        mvc.perform(get("/api/reservations").header("Origin", "http://example.com"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test void allowsGetPreflightButRejectsPost() throws Exception {
        mvc.perform(options("/api/reservations").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Methods", "GET"));
        mvc.perform(options("/api/reservations").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden());
    }
}
