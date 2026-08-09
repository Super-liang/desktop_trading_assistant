package com.tradingassistant.portfolio;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PortfolioController.class)
public class PortfolioExceptionHandler {
    @ExceptionHandler(PositionAlreadyExistsException.class)
    ProblemDetail duplicate(PositionAlreadyExistsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("持仓已存在");
        problem.setProperty("code", "POSITION_ALREADY_EXISTS");
        problem.setProperty("existingPositionId", exception.existingPositionId());
        return problem;
    }
}
