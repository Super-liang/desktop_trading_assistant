from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Iterable

import pandas as pd


class ContractViolation(RuntimeError):
    """AKShare 返回结构与固定契约不一致。"""


class CatalogMarket(str, Enum):
    A_SHARE = "A_SHARE"
    HK_STOCK = "HK_STOCK"
    US_STOCK = "US_STOCK"
    PUBLIC_FUND = "PUBLIC_FUND"


US_EASTMONEY_EXCHANGES = {
    "105": "NASDAQ",
    "106": "NYSE",
    "107": "AMEX",
}


@dataclass(frozen=True)
class IndexDefinition:
    instrument_id: str
    code: str
    name: str
    groups: tuple[str, ...]


INDEX_DEFINITIONS: tuple[IndexDefinition, ...] = (
    IndexDefinition("SSE_INDEX:000001", "000001", "上证指数", ("上证系列指数", "沪深重要指数")),
    IndexDefinition("SZSE_INDEX:399001", "399001", "深证成指", ("深证系列指数", "沪深重要指数")),
    IndexDefinition("SZSE_INDEX:399006", "399006", "创业板指", ("深证系列指数", "沪深重要指数")),
    IndexDefinition("BSE_INDEX:899050", "899050", "北证50", ("沪深重要指数", "指数成份")),
    IndexDefinition("SSE_INDEX:000680", "000680", "科创综指", ("上证系列指数",)),
    IndexDefinition("SSE_INDEX:000688", "000688", "科创50", ("上证系列指数", "沪深重要指数")),
    IndexDefinition("CSI_INDEX:000300", "000300", "沪深300", ("中证系列指数", "沪深重要指数")),
)

INDEX_BY_CODE = {item.code: item for item in INDEX_DEFINITIONS}
INDEX_COLUMNS = {"代码", "名称", "最新价", "涨跌额", "涨跌幅", "今开", "昨收"}

FRAME_CONTRACTS: dict[str, frozenset[str]] = {
    "A_SHARE_EASTMONEY": frozenset({"代码", "名称", "最新价", "涨跌额", "涨跌幅", "今开", "昨收"}),
    "A_SHARE_SINA": frozenset({"代码", "名称", "最新价", "涨跌额", "涨跌幅", "今开", "昨收"}),
    "HK_STOCK_EASTMONEY": frozenset({"代码", "名称", "最新价", "涨跌额", "涨跌幅", "今开", "昨收"}),
    "HK_STOCK_SINA": frozenset({
        "代码", "中文名称", "最新价", "涨跌额", "涨跌幅", "今开", "昨收",
    }),
    "US_STOCK_EASTMONEY": frozenset({"代码", "名称", "最新价", "涨跌额", "涨跌幅", "开盘价", "昨收价"}),
    "US_STOCK_SINA": frozenset({
        "name", "cname", "symbol", "price", "diff", "chg", "preclose",
        "open", "high", "low", "volume", "market",
    }),
    "OPEN_FUND_CATALOG": frozenset({"基金代码", "基金简称", "基金类型"}),
    "OPEN_FUND_NAV": frozenset({"基金代码", "单位净值", "净值日期"}),
    "A_SHARE_INDEX": frozenset(INDEX_COLUMNS),
}


def normalize_catalog(frame: pd.DataFrame, market: CatalogMarket) -> dict[str, dict[str, str]]:
    """将各 AKShare 目录规范为带市场、交易所、币种和上游代码的稳定契约。"""
    if market == CatalogMarket.A_SHARE:
        validate_columns(frame, {"code", "name"}, "A 股证券目录")
        return _normalize_a_share_catalog(frame)
    if market == CatalogMarket.HK_STOCK:
        validate_columns(frame, {"代码", "名称"}, "港股证券目录")
        return _normalize_hk_catalog(frame)
    if market == CatalogMarket.US_STOCK:
        validate_columns(frame, {"代码", "名称"}, "美股证券目录")
        return _normalize_us_catalog(frame)
    validate_frame_contract(frame, "OPEN_FUND_CATALOG")
    return _normalize_fund_catalog(frame)


def _normalize_a_share_catalog(frame: pd.DataFrame) -> dict[str, dict[str, str]]:
    from .market import InvalidMarketData, asset_type_for, exchange_for, normalize_code

    result: dict[str, dict[str, str]] = {}
    for row in frame.to_dict(orient="records"):
        try:
            code = normalize_code(row["code"])
            exchange = exchange_for(code)
            name = _name(row["name"])
        except (InvalidMarketData, ValueError, TypeError):
            continue
        result[f"{exchange}:{code}"] = _catalog_row(
            code, name, CatalogMarket.A_SHARE, exchange, "CNY",
            asset_type_for(code), code,
        )
    return result


def _normalize_hk_catalog(frame: pd.DataFrame) -> dict[str, dict[str, str]]:
    result: dict[str, dict[str, str]] = {}
    for row in frame.to_dict(orient="records"):
        raw_code = str(row["代码"]).strip()
        code = raw_code.zfill(5)
        try:
            name = _name(row["名称"])
        except ValueError:
            continue
        if not code.isdigit() or len(code) != 5:
            continue
        result[f"HKEX:{code}"] = _catalog_row(
            code, name, CatalogMarket.HK_STOCK, "HKEX", "HKD", "STOCK", raw_code,
        )
    return result


def _normalize_us_catalog(frame: pd.DataFrame) -> dict[str, dict[str, str]]:
    result: dict[str, dict[str, str]] = {}
    for row in frame.to_dict(orient="records"):
        provider_symbol = str(row["代码"]).strip().upper()
        if "." not in provider_symbol:
            continue
        market_code, code = provider_symbol.split(".", 1)
        exchange = US_EASTMONEY_EXCHANGES.get(market_code)
        try:
            name = _name(row["名称"])
        except ValueError:
            continue
        if exchange is None or not _valid_us_code(code):
            continue
        result[f"{exchange}:{code}"] = _catalog_row(
            code, name, CatalogMarket.US_STOCK, exchange, "USD", "STOCK",
            provider_symbol,
        )
    return result


def _normalize_fund_catalog(frame: pd.DataFrame) -> dict[str, dict[str, str]]:
    result: dict[str, dict[str, str]] = {}
    for row in frame.to_dict(orient="records"):
        code = str(row["基金代码"]).strip().zfill(6)
        fund_type = str(row["基金类型"]).strip()
        try:
            name = _name(row["基金简称"])
        except ValueError:
            continue
        if (not code.isdigit() or len(code) != 6
                or any(word in fund_type for word in ("货币", "封闭", "理财"))):
            continue
        result[f"CN_FUND:{code}"] = _catalog_row(
            code, name, CatalogMarket.PUBLIC_FUND, "CN_FUND", "CNY",
            "OPEN_END_FUND", code,
        )
    return result


def _catalog_row(code: str, name: str, market: CatalogMarket, exchange: str,
                 currency: str, asset_type: str, provider_symbol: str) -> dict[str, str]:
    return {
        "code": code,
        "name": name,
        "market": market.value,
        "exchange": exchange,
        "currency": currency,
        "assetType": asset_type,
        "providerSymbol": provider_symbol,
    }


def _name(value: object) -> str:
    name = str(value).strip()
    if not name or name.lower() == "nan":
        raise ValueError("证券名称为空")
    return name


def _valid_us_code(code: str) -> bool:
    import re
    return re.fullmatch(r"[A-Z][A-Z0-9.-]{0,9}", code) is not None


def validate_columns(frame: pd.DataFrame, required: set[str], contract_name: str) -> None:
    missing = sorted(required.difference(str(column) for column in frame.columns))
    if missing:
        raise ContractViolation(f"{contract_name}契约缺少列: {', '.join(missing)}")


def validate_frame_contract(frame: pd.DataFrame, contract: str) -> None:
    try:
        required = FRAME_CONTRACTS[contract]
    except KeyError as exception:
        raise ValueError(f"未知 AKShare 契约: {contract}") from exception
    validate_columns(frame, set(required), contract)


def normalize_index_frames(
    frames: Iterable[pd.DataFrame], source: str, quote_as_of: str
) -> dict[str, dict]:
    normalized: dict[str, dict] = {}
    for frame in frames:
        validate_columns(frame, INDEX_COLUMNS, "指数")
        for row in frame.to_dict(orient="records"):
            code = str(row["代码"]).strip().lower()
            if code.startswith(("sh", "sz", "bj")):
                code = code[2:]
            code = code.zfill(6)
            definition = INDEX_BY_CODE.get(code)
            if definition is None or str(row["名称"]).strip() != definition.name:
                continue
            normalized[definition.instrument_id] = {
                "instrumentId": definition.instrument_id,
                "code": code,
                "name": definition.name,
                "price": _number(row["最新价"]),
                "change": _number(row["涨跌额"]),
                "changePercent": _number(row["涨跌幅"]),
                "open": _number(row["今开"]),
                "previousClose": _number(row["昨收"]),
                "source": source,
                "quoteAsOf": quote_as_of,
            }
    return normalized


def _number(value: object) -> float | None:
    if pd.isna(value):
        return None
    return float(value)
