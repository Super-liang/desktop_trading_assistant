package com.tradingassistant.portfolio;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioExceptionHandlerTest {
    @Test
    void duplicateProblemIsStructuredConflict() {
        UUID existingId = UUID.randomUUID();

        var problem = new PortfolioExceptionHandler().duplicate(
                new PositionAlreadyExistsException(existingId));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).contains("已存在");
        assertThat(problem.getProperties()).containsEntry("code", "POSITION_ALREADY_EXISTS")
                .containsEntry("existingPositionId", existingId);
    }
}
