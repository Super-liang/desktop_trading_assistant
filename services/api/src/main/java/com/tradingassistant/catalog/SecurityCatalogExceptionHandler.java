package com.tradingassistant.catalog;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SecurityCatalogController.class)
public class SecurityCatalogExceptionHandler {
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail unavailable(IllegalStateException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        detail.setType(URI.create("urn:problem:security-catalog-unavailable"));
        return detail;
    }
}
