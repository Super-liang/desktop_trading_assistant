package com.tradingassistant.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundNavQuoteRepository extends JpaRepository<FundNavQuote, FundNavQuoteId> {
    List<FundNavQuote> findByInstrumentIdOrderByNavDateDesc(String instrumentId);
    List<FundNavQuote> findTop2ByInstrumentIdOrderByNavDateDesc(String instrumentId);
}
