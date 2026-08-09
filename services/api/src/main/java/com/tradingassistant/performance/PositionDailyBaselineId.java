package com.tradingassistant.performance;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

public record PositionDailyBaselineId(UUID positionId, LocalDate tradingDate)
        implements Serializable {}
