package com.tradingassistant.portfolio;

import java.util.UUID;

public class PositionAlreadyExistsException extends RuntimeException {
    private final UUID existingPositionId;

    public PositionAlreadyExistsException(UUID existingPositionId) {
        super("该持仓已存在，是否累加持仓");
        this.existingPositionId = existingPositionId;
    }

    public UUID existingPositionId() {
        return existingPositionId;
    }
}
