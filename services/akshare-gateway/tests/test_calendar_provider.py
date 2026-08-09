from __future__ import annotations

from datetime import date

from akshare_gateway.calendar_provider import ExchangeCalendarProvider


def test_generates_sessions_for_all_stock_markets() -> None:
    provider = ExchangeCalendarProvider()
    sessions = provider.sessions(date(2024, 11, 28), date(2024, 12, 2))

    assert {item.market for item in sessions} == {"A_SHARE", "HK_STOCK", "US_STOCK"}
    assert all(item.open_at.tzinfo is not None and item.close_at.tzinfo is not None for item in sessions)


def test_hong_kong_session_contains_lunch_break() -> None:
    provider = ExchangeCalendarProvider()
    session = next(
        item for item in provider.sessions(date(2024, 11, 28), date(2024, 11, 28))
        if item.market == "HK_STOCK"
    )

    assert session.break_start_at is not None
    assert session.break_end_at is not None


def test_shanghai_session_contains_lunch_break() -> None:
    provider = ExchangeCalendarProvider()
    session = next(
        item for item in provider.sessions(date(2024, 11, 28), date(2024, 11, 28))
        if item.market == "A_SHARE"
    )

    assert session.break_start_at is not None
    assert session.break_end_at is not None


def test_us_thanksgiving_is_not_a_session() -> None:
    provider = ExchangeCalendarProvider()
    sessions = provider.sessions(date(2024, 11, 28), date(2024, 11, 28))

    assert not any(item.market == "US_STOCK" for item in sessions)


def test_new_york_dst_changes_utc_open_time() -> None:
    provider = ExchangeCalendarProvider()
    winter = next(item for item in provider.sessions(date(2024, 1, 8), date(2024, 1, 8)) if item.market == "US_STOCK")
    summer = next(item for item in provider.sessions(date(2024, 7, 8), date(2024, 7, 8)) if item.market == "US_STOCK")

    assert winter.open_at.hour == 14
    assert summer.open_at.hour == 13


def test_new_york_early_close_is_flagged() -> None:
    provider = ExchangeCalendarProvider()
    session = next(item for item in provider.sessions(date(2024, 11, 29), date(2024, 11, 29)) if item.market == "US_STOCK")

    assert session.early_close is True
    assert session.close_at.hour == 18


def test_a_share_cross_check_reports_provider_drift() -> None:
    provider = ExchangeCalendarProvider()
    result = provider.cross_check_a_share(
        date(2024, 11, 28), date(2024, 11, 29), {date(2024, 11, 28)}
    )

    assert result["onlyExchangeCalendars"] == ["2024-11-29"]
    assert result["onlyAkshare"] == []
