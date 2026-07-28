package com.tradingassistant.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

public interface UserOperationAuditRepository extends JpaRepository<UserOperationAudit, UUID>,
        JpaSpecificationExecutor<UserOperationAudit> {
    default Page<UserOperationAudit> search(UUID userId, UserOperationAudit.Action action,
            Instant fromTime, Instant toTime, Pageable pageable) {
        Specification<UserOperationAudit> filters = (root, query, builder) ->
                builder.equal(root.get("userId"), userId);
        if (action != null) {
            filters = filters.and((root, query, builder) ->
                    builder.equal(root.get("action"), action));
        }
        if (fromTime != null) {
            filters = filters.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("createdAt"), fromTime));
        }
        if (toTime != null) {
            filters = filters.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("createdAt"), toTime));
        }
        return findAll(filters, pageable);
    }
}
