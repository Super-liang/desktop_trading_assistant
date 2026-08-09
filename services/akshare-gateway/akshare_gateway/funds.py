from __future__ import annotations

from datetime import date, datetime
import re

import pandas as pd

from .market import InvalidMarketData


UNIT_NAV_COLUMN = re.compile(r"^(\d{4}-\d{2}-\d{2})-单位净值$")


def normalize_current_unit_nav(frame: pd.DataFrame, fetched_at: datetime) -> dict[str, dict]:
    if not {"基金代码", "基金简称"}.issubset(frame.columns):
        raise InvalidMarketData("开放式基金净值缺少基金代码或基金简称")
    dated_columns = sorted(
        ((date.fromisoformat(match.group(1)), str(column))
         for column in frame.columns
         if (match := UNIT_NAV_COLUMN.fullmatch(str(column)))),
        reverse=True,
    )
    if not dated_columns:
        raise InvalidMarketData("开放式基金净值缺少单位净值日期列")
    result: dict[str, dict] = {}
    for row in frame.to_dict(orient="records"):
        code = str(row["基金代码"]).strip().zfill(6)
        if not code.isdigit() or len(code) != 6:
            continue
        values = [(nav_date, _positive(row.get(column)))
                  for nav_date, column in dated_columns]
        available = [(nav_date, value) for nav_date, value in values if value is not None]
        if not available:
            continue
        latest_date, latest_nav = available[0]
        previous = available[1] if len(available) > 1 else (None, None)
        result[f"CN_FUND:{code}"] = _row(
            code, row["基金简称"], latest_date, latest_nav,
            previous[0], previous[1], fetched_at,
        )
    return result


def normalize_fund_history(
    frame: pd.DataFrame, code: str, name: str, fetched_at: datetime
) -> dict | None:
    if not {"净值日期", "单位净值"}.issubset(frame.columns):
        raise InvalidMarketData("基金历史净值缺少净值日期或单位净值")
    available = []
    for row in frame.to_dict(orient="records"):
        nav = _positive(row["单位净值"])
        if nav is None:
            continue
        available.append((pd.Timestamp(row["净值日期"]).date(), nav))
    available.sort(reverse=True)
    if not available:
        return None
    previous = available[1] if len(available) > 1 else (None, None)
    return _row(code, name, available[0][0], available[0][1],
                previous[0], previous[1], fetched_at)


def _row(code: str, name: object, nav_date: date, unit_nav: float,
         previous_date: date | None, previous_nav: float | None,
         fetched_at: datetime) -> dict:
    return {
        "instrumentId": f"CN_FUND:{code}",
        "code": code,
        "name": str(name).strip() or code,
        "unitNav": unit_nav,
        "navDate": nav_date.isoformat(),
        "previousUnitNav": previous_nav,
        "previousNavDate": previous_date.isoformat() if previous_date else None,
        "source": "AKSHARE_EASTMONEY_UNIT_NAV",
        "sourceUpdatedAt": fetched_at.isoformat(),
    }


def _positive(value: object) -> float | None:
    if value is None or pd.isna(value) or value in ("", "-"):
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if number > 0 else None
