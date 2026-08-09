package com.tradingassistant.marketdata;

import com.tradingassistant.admin.AdminAudit;
import com.tradingassistant.admin.AdminAuditRepository;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MarketDataConfigService {
    private final MarketDataConfigRepository repository;
    private final AdminAuditRepository audits;

    public MarketDataConfigService(MarketDataConfigRepository repository,
            AdminAuditRepository audits) {
        this.repository = repository;
        this.audits = audits;
    }

    @Transactional
    public MarketDataConfig current() {
        MarketDataConfig config = repository.findById(1)
                .orElseGet(() -> repository.save(MarketDataConfig.defaults()));
        // 滚动升级时将数据库中的历史东财全市场配置自动迁移为新浪。
        config.normalizeSnapshotSource();
        return config;
    }

    @Transactional
    public MarketDataConfig update(UUID actorId, UpdateRequest request) {
        validate(request);
        MarketDataConfig config = current();
        config.update(request.provider(), request.mode(), MarketDataConfig.SnapshotSource.SINA,
                request.singleSource(), request.refreshSeconds());
        audits.save(new AdminAudit(actorId, "MARKET_DATA_CONFIG_UPDATED", null, "SUCCESS"));
        return config;
    }

    private void validate(UpdateRequest request) {
        if (request.provider() == null || request.mode() == null
                || request.singleSource() == null) {
            throw new IllegalArgumentException("行情源配置字段不能为空");
        }
        if (request.refreshSeconds() < 30 || request.refreshSeconds() > 300) {
            throw new IllegalArgumentException("全市场刷新频率必须在 30 到 300 秒之间");
        }
    }

    public record UpdateRequest(MarketDataConfig.Provider provider, MarketDataConfig.Mode mode,
            MarketDataConfig.SnapshotSource snapshotSource,
            MarketDataConfig.SingleSource singleSource, int refreshSeconds) {}
}
