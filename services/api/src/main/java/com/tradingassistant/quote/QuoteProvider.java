package com.tradingassistant.quote;

import java.util.List;
import java.util.Set;

public interface QuoteProvider {
    String id();
    int priority();
    boolean healthy();
    boolean demo();
    Set<InstrumentId.Exchange> exchanges();
    List<InstrumentSearchResult> search(String query);
    List<Quote> snapshots(List<InstrumentId> instruments);
    default Capabilities capabilities() {
        return new Capabilities(exchanges(), true, true, false, 50,
                demo() ? "DEMO_ONLY" : "LICENSE_REQUIRED");
    }

    record InstrumentSearchResult(String instrumentId, String code, String name,
                                  InstrumentId.Exchange exchange, InstrumentId.AssetType assetType) {}
    record Capabilities(Set<InstrumentId.Exchange> exchanges, boolean search, boolean snapshots,
                        boolean streaming, int maxSymbols, String authorizedUse) {}
}
