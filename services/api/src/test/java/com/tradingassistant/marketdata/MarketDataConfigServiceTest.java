package com.tradingassistant.marketdata;

import com.tradingassistant.admin.AdminAuditRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataConfigServiceTest {
    @Mock MarketDataConfigRepository repository;
    @Mock AdminAuditRepository audits;

    @Test
    void updatesSystemConfigAndWritesAudit() {
        MarketDataConfig config = MarketDataConfig.defaults();
        when(repository.findById(1)).thenReturn(Optional.of(config));
        var service = new MarketDataConfigService(repository, audits);

        MarketDataConfig updated = service.update(UUID.randomUUID(), new MarketDataConfigService.UpdateRequest(
                MarketDataConfig.Provider.AKSHARE,
                MarketDataConfig.Mode.SINGLE_STOCK,
                MarketDataConfig.SnapshotSource.EASTMONEY,
                MarketDataConfig.SingleSource.XUEQIU,
                30));

        assertThat(updated.getMode()).isEqualTo(MarketDataConfig.Mode.SINGLE_STOCK);
        assertThat(updated.getSnapshotSource()).isEqualTo(MarketDataConfig.SnapshotSource.SINA);
        assertThat(updated.getSingleSource()).isEqualTo(MarketDataConfig.SingleSource.XUEQIU);
        verify(audits).save(any());
    }

    @Test
    void rejectsUnsafeSnapshotFrequencyWithoutPersistingOrAuditing() {
        var service = new MarketDataConfigService(repository, audits);

        assertThatThrownBy(() -> service.update(UUID.randomUUID(),
                new MarketDataConfigService.UpdateRequest(
                        MarketDataConfig.Provider.AKSHARE,
                        MarketDataConfig.Mode.MARKET_SNAPSHOT,
                        MarketDataConfig.SnapshotSource.EASTMONEY,
                        MarketDataConfig.SingleSource.EASTMONEY,
                        10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30");
        verifyNoInteractions(repository, audits);
    }
}
