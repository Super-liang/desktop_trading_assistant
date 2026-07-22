package com.tradingassistant.quote;

import com.tradingassistant.marketdata.MarketDataConfig;

/** 单次行情请求选项；不可修改，避免并发用户之间共享来源状态。 */
public record QuoteRequestOptions(MarketDataConfig.Mode mode,
                                  MarketDataConfig.SnapshotSource snapshotSource,
                                  MarketDataConfig.SingleSource singleSource) {
    public static final QuoteRequestOptions DEFAULT = new QuoteRequestOptions(null, null, null);
}
