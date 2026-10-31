package jp.rentaldesk.reservation;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class})
    public ResponseEntity<ProblemDetail> badRequest(RuntimeException ex) {
        String detail = ex instanceof ArithmeticException ? "料金の計算結果が上限を超えています" : ex.getMessage();
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail));
    }
}
