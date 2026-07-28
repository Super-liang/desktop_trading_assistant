package com.tradingassistant.performance;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ChinaTradingCalendar {
    // 版本内置交易所休市日；跨年度发布时需依据交易所公告更新。
    private static final Set<LocalDate> HOLIDAYS = Set.of(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17),
            LocalDate.of(2026, 2, 18), LocalDate.of(2026, 2, 19),
            LocalDate.of(2026, 2, 20), LocalDate.of(2026, 4, 6),
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 4),
            LocalDate.of(2026, 5, 5), LocalDate.of(2026, 6, 19),
            LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 5),
            LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7));

    public boolean isTradingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
                && !HOLIDAYS.contains(date);
    }
}
