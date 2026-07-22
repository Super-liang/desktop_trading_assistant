package com.tradingassistant.quote;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {QuoteController.class})
public class QuoteExceptionHandler {
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail unavailable(IllegalStateException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "真实行情暂不可用，请检查行情源状态");
        detail.setType(URI.create("urn:problem:quote-source-unavailable"));
        return detail;
    }
}
