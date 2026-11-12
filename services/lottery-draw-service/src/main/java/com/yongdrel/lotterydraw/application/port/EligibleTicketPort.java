package com.yongdrel.lotterydraw.application.port;

import java.util.List;
import java.util.UUID;

public interface EligibleTicketPort {

    List<UUID> getEligibleTicketIds(UUID eventId);
}
