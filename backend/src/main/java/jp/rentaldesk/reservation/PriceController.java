package jp.rentaldesk.reservation;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prices")
public class PriceController {
    private final ReservationRepository repository;
    public PriceController(ReservationRepository repository) { this.repository = repository; }

    @GetMapping
    public List<PriceResponse> list() { return repository.findAllPrices(); }
}
