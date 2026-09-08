package com.ntccpay.auth.domain.service;

import com.ntccpay.auth.domain.model.Decision;
import com.ntccpay.auth.domain.model.ReasonCode;

/** The outcome of evaluating the business rules for one authorization. */
public record DecisionResult(Decision decision, ReasonCode reasonCode) {

    public static DecisionResult approved() {
        return new DecisionResult(Decision.APPROVED, null);
    }

    public static DecisionResult declined(ReasonCode reasonCode) {
        return new DecisionResult(Decision.DECLINED, reasonCode);
    }
}
