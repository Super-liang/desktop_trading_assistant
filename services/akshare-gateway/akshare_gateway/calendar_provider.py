from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import date, datetime, time

import exchange_calendars as xcals
import pandas as pd


@dataclass(frozen=True)
class MarketSession:
    market: str
    trading_date: date
    timezone: str
    open_at: datetime
    break_start_at: datetime | None
    break_end_at: datetime | None
    close_at: datetime
    early_close: bool
    source: str = "exchange_calendars:4.13.2"

    def to_dict(self) -> dict:
        result = asdict(self)
        for key in ("trading_date", "open_at", "break_start_at", "break_end_at", "close_at"):
            value = result[key]
            result[key] = value.isoformat() if value is not None else None
        return result


class ExchangeCalendarProvider:
    CALENDARS = {
        "A_SHARE": ("XSHG", "Asia/Shanghai", time(15, 0)),
        "HK_STOCK": ("XHKG", "Asia/Hong_Kong", time(16, 0)),
        "US_STOCK": ("XNYS", "America/New_York", time(16, 0)),
    }

    def sessions(self, start: date, end: date) -> list[MarketSession]:
        if end < start:
            raise ValueError("日历结束日期不能早于开始日期")
        result: list[MarketSession] = []
        for market, (calendar_name, timezone, regular_close) in self.CALENDARS.items():
            calendar = xcals.get_calendar(calendar_name)
            schedule = calendar.schedule.loc[start.isoformat():end.isoformat()]
            for session_date, row in schedule.iterrows():
                open_at = _utc_datetime(row["open"])
                close_at = _utc_datetime(row["close"])
                local_close = close_at.astimezone(__import__("zoneinfo").ZoneInfo(timezone)).time().replace(tzinfo=None)
                result.append(MarketSession(
                    market=market,
                    trading_date=session_date.date(),
                    timezone=timezone,
                    open_at=open_at,
                    break_start_at=_optional_utc_datetime(row.get("break_start")),
                    break_end_at=_optional_utc_datetime(row.get("break_end")),
                    close_at=close_at,
                    early_close=local_close < regular_close,
                ))
        return sorted(result, key=lambda item: (item.trading_date, item.market))

    def cross_check_a_share(
        self, start: date, end: date, akshare_dates: set[date]
    ) -> dict[str, list[str]]:
        generated = {
            item.trading_date
            for item in self.sessions(start, end)
            if item.market == "A_SHARE"
        }
        expected = {item for item in akshare_dates if start <= item <= end}
        return {
            "onlyExchangeCalendars": sorted(item.isoformat() for item in generated - expected),
            "onlyAkshare": sorted(item.isoformat() for item in expected - generated),
        }


def _utc_datetime(value: object) -> datetime:
    return pd.Timestamp(value).to_pydatetime()


def _optional_utc_datetime(value: object) -> datetime | None:
    if value is None or pd.isna(value):
        return None
    return _utc_datetime(value)
