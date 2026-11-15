package com.yongdrel.fraudriskservice.application.service;

import com.yongdrel.fraudriskservice.application.exception.ForbiddenOperationException;
import com.yongdrel.fraudriskservice.application.exception.ResourceNotFoundException;
import com.yongdrel.fraudriskservice.application.model.FlagSearchCriteria;
import com.yongdrel.fraudriskservice.application.model.PageResult;
import com.yongdrel.fraudriskservice.application.port.in.RiskFlagUseCase;
import com.yongdrel.fraudriskservice.application.port.out.FlagEventPublisherPort;
import com.yongdrel.fraudriskservice.application.port.out.RiskFlagRepositoryPort;
import com.yongdrel.fraudriskservice.domain.model.Actor;
import com.yongdrel.fraudriskservice.domain.model.FlagStatus;
import com.yongdrel.fraudriskservice.domain.model.RiskFlag;
import com.yongdrel.fraudriskservice.domain.model.Role;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class FlagReviewService implements RiskFlagUseCase {

    private final RiskFlagRepositoryPort flags;
    private final FlagEventPublisherPort publisher;
    private final Clock clock;

    public FlagReviewService(RiskFlagRepositoryPort flags, FlagEventPublisherPort publisher, Clock clock) {
        this.flags = flags;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RiskFlag> search(FlagSearchCriteria criteria, int page, int size, Actor actor) {
        AccessPolicy.requireRiskReader(actor);
        FlagSearchCriteria scoped = criteria;
        if (actor.is(Role.ORGANIZER)) {
            if (criteria.tenantId() != null) {
                AccessPolicy.requireOwnTenant(actor, criteria.tenantId());
            } else if (actor.tenantId() == null) {
                throw new ForbiddenOperationException("Organizer token has no tenantId claim");
            }
            scoped = criteria.withTenantId(actor.tenantId());
        }
        return flags.search(scoped, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public RiskFlag get(Long flagId, Actor actor) {
        AccessPolicy.requireRiskReader(actor);
        return findVisible(flagId, actor);
    }

    @Override
    @Transactional
    public RiskFlag review(Long flagId, FlagStatus status, String note, Actor actor) {
        AccessPolicy.requireReviewer(actor);
        RiskFlag flag = findVisible(flagId, actor);
        flag.review(status, actor.userId(), note, clock.instant());
        RiskFlag saved = flags.save(flag);
        publisher.flagReviewed(saved);
        return saved;
    }

    /** Flags of other tenants are reported as 404 so their existence is not revealed. */
    private RiskFlag findVisible(Long flagId, Actor actor) {
        return flags.findById(flagId)
                .filter(flag -> AccessPolicy.canViewTenant(actor, flag.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Flag " + flagId + " not found"));
    }
}
