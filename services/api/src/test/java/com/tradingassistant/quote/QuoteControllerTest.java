package com.tradingassistant.quote;

import com.tradingassistant.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuoteControllerTest {
    @Test
    void rejectsUnknownSnapshotSourceBeforeCallingProvider() throws Exception {
        QuoteProviderRegistry registry = mock(QuoteProviderRegistry.class);
        AppProperties properties = new AppProperties(null, null,
                new AppProperties.Quotes(30, 2000,
                        new AppProperties.Quotes.HttpProvider(false, "", "", 10)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new QuoteController(registry, properties)).build();

        mvc.perform(get("/api/v1/quotes/snapshots")
                        .param("symbols", "SSE:600519")
                        .param("snapshotSource", "UNTRUSTED"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registry);
    }

    @Test
    void passesValidatedSingleSourceToProvider() throws Exception {
        QuoteProviderRegistry registry = mock(QuoteProviderRegistry.class);
        AppProperties properties = new AppProperties(null, null,
                new AppProperties.Quotes(30, 2000,
                        new AppProperties.Quotes.HttpProvider(false, "", "", 10)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new QuoteController(registry, properties)).build();

        mvc.perform(get("/api/v1/quotes/snapshots")
                        .param("symbols", "SSE:600519")
                        .param("mode", "SINGLE_STOCK")
                        .param("singleSource", "XUEQIU"))
                .andExpect(status().isOk());

        verify(registry).snapshots(anyList(), argThat(options ->
                options.mode() == com.tradingassistant.marketdata.MarketDataConfig.Mode.SINGLE_STOCK
                && options.singleSource()
                        == com.tradingassistant.marketdata.MarketDataConfig.SingleSource.XUEQIU));
    }

    @Test
    void rejectsUnknownModeBeforeCallingProvider() throws Exception {
        QuoteProviderRegistry registry = mock(QuoteProviderRegistry.class);
        AppProperties properties = new AppProperties(null, null,
                new AppProperties.Quotes(30, 2000,
                        new AppProperties.Quotes.HttpProvider(false, "", "", 10)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new QuoteController(registry, properties)).build();

        mvc.perform(get("/api/v1/quotes/snapshots")
                        .param("symbols", "SSE:600519")
                        .param("mode", "UNTRUSTED"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registry);
    }

    @Test
    void rejectsUnknownSingleSourceBeforeCallingProvider() throws Exception {
        QuoteProviderRegistry registry = mock(QuoteProviderRegistry.class);
        AppProperties properties = new AppProperties(null, null,
                new AppProperties.Quotes(30, 2000,
                        new AppProperties.Quotes.HttpProvider(false, "", "", 10)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new QuoteController(registry, properties)).build();

        mvc.perform(get("/api/v1/quotes/snapshots")
                        .param("symbols", "SSE:600519")
                        .param("singleSource", "UNTRUSTED"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registry);
    }
}
