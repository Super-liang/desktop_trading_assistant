from __future__ import annotations

import re
from datetime import datetime, timezone
from math import isfinite
from typing import Callable

import requests
from zoneinfo import ZoneInfo

US_INSTRUMENT_PATTERN = re.compile(
    r"^(NASDAQ|NYSE|AMEX):([A-Z][A-Z0-9.-]{0,9})$"
)
SINA_LINE_PATTERN = re.compile(r'^var hq_str_gb_([a-z0-9.]+)="(.*)";$')
SINA_US_QUOTE_URL = "https://hq.sinajs.cn/list={symbols}"
SINA_HEADERS = {
    "Referer": "https://finance.sina.com.cn/stock/usstock/",
    "User-Agent": "Mozilla/5.0",
}


def validate_us_instruments(raw_instruments: list[str]) -> list[str]:
    """校验并去重规范美股标识，拒绝把任意文本带给上游。"""
    result: list[str] = []
    for raw in raw_instruments:
        instrument = raw.strip().upper()
        if US_INSTRUMENT_PATTERN.fullmatch(instrument) is None:
            raise ValueError("美股证券标识格式无效")
        if instrument not in result:
            result.append(instrument)
    return result


def fetch_us_position_quotes(
    instruments: list[str],
    *,
    timeout_seconds: float = 10,
    batch_size: int = 80,
    http_get: Callable[..., requests.Response] = requests.get,
) -> list[dict]:
    normalized = validate_us_instruments(instruments)
    requested_by_symbol = {
        instrument.split(":", 1)[1].lower(): instrument for instrument in normalized
    }
    quotes: dict[str, dict] = {}
    for offset in range(0, len(normalized), batch_size):
        batch = normalized[offset: offset + batch_size]
        provider_symbols = ",".join(
            f"gb_{instrument.split(':', 1)[1].lower()}" for instrument in batch
        )
        response = http_get(
            SINA_US_QUOTE_URL.format(symbols=provider_symbols),
            headers=SINA_HEADERS,
            timeout=timeout_seconds,
        )
        response.raise_for_status()
        body = response.content.decode("gb18030", errors="replace")
        for line in body.splitlines():
            parsed = _parse_line(line.strip(), requested_by_symbol)
            if parsed is not None:
                quotes[parsed["instrumentId"]] = parsed
    return [quotes[instrument] for instrument in normalized if instrument in quotes]


def _parse_line(line: str, requested_by_symbol: dict[str, str]) -> dict | None:
    matched = SINA_LINE_PATTERN.fullmatch(line)
    if matched is None:
        return None
    symbol, payload = matched.groups()
    instrument_id = requested_by_symbol.get(symbol.lower())
    if instrument_id is None or not payload:
        return None
    fields = payload.split(",")
    if len(fields) < 27:
        return None
    try:
        last = _number(fields[1])
        previous_close = _number(fields[26])
        if last <= 0 or previous_close <= 0:
            return None
        change = _number(fields[4], last - previous_close)
        change_percent = _number(
            fields[2], change / previous_close * 100 if previous_close else 0
        )
        source_timestamp = datetime.strptime(
            fields[3].strip(), "%Y-%m-%d %H:%M:%S"
        ).replace(tzinfo=ZoneInfo("Asia/Shanghai")).astimezone(timezone.utc)
    except (TypeError, ValueError):
        return None
    received_at = datetime.now(timezone.utc)
    return {
        "instrumentId": instrument_id,
        "name": fields[0].strip() or instrument_id.split(":", 1)[1],
        "last": last,
        "previousClose": previous_close,
        "open": _number(fields[5], 0),
        "high": _number(fields[6], 0),
        "low": _number(fields[7], 0),
        "change": change,
        "changePercent": change_percent,
        "volume": _number(fields[10], 0),
        "marketPhase": "UNKNOWN",
        "source": "AKSHARE_SINA_POSITION",
        "sourceTimestamp": source_timestamp,
        "receivedAt": received_at,
        "delayed": True,
        "stale": False,
        "demo": False,
    }


def _number(value: object, default: float | None = None) -> float:
    try:
        number = float(str(value).strip())
    except (TypeError, ValueError):
        if default is not None:
            return default
        raise
    if not isfinite(number):
        if default is not None:
            return default
        raise ValueError("行情数值无效")
    return number
