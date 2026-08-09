package com.tradingassistant.audit;

import java.util.UUID;
import java.time.LocalDate;
import com.tradingassistant.market.Market;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserOperationAuditService {
    private final UserOperationAuditRepository repository;
    public UserOperationAuditService(UserOperationAuditRepository repository) {
        this.repository = repository;
    }
    @Transactional
    public void record(UUID userId, UserOperationAudit.Action action, UUID portfolioItemId,
            String instrumentId, String instrumentName, UserOperationAudit.Result result) {
        repository.save(new UserOperationAudit(userId, action, portfolioItemId,
                instrumentId, instrumentName, result));
    }

    @Transactional
    public void record(UUID userId, UserOperationAudit.Action action, UUID portfolioItemId,
            String instrumentId, String instrumentName, Market market, LocalDate openedOn,
            UserOperationAudit.Result result) {
        repository.save(new UserOperationAudit(userId, action, portfolioItemId,
                instrumentId, instrumentName, market, openedOn, result));
    }

    /** 失败审计必须独立提交，不能随调用方抛出的业务异常一起回滚。 */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId, UserOperationAudit.Action action) {
        repository.save(new UserOperationAudit(userId, action, null,
                null, null, UserOperationAudit.Result.FAILURE));
    }
}
