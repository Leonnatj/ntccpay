package com.ntccpay.auth.domain.event;

import java.time.Instant;

/** Something meaningful that happened in the domain. */
public interface DomainEvent {

    Instant occurredAt();
}
