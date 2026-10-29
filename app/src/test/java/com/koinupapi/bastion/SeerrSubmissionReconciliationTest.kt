package com.koinupapi.bastion

import org.junit.Assert.assertEquals
import org.junit.Test

class SeerrSubmissionReconciliationTest {
    private val decision = SeerrSubmissionReconciliation

    @Test fun newlyCreatedRequestIsDetected() {
        assertEquals(decision.Outcome.NEW_REQUEST_FOUND,
            decision.classify(setOf(26), setOf(26, 27)))
    }

    @Test fun existingRequestIsNotMisreportedAsNew() {
        assertEquals(decision.Outcome.EXISTING_REQUEST_FOUND,
            decision.classify(setOf(26), setOf(26)))
    }

    @Test fun failedPreflightDoesNotClaimNewRequest() {
        assertEquals(decision.Outcome.EXISTING_REQUEST_FOUND,
            decision.classify(null, setOf(26)))
    }

    @Test fun emptyResponseIsUnconfirmed() {
        assertEquals(decision.Outcome.UNCONFIRMED,
            decision.classify(setOf(26), emptySet()))
    }

    @Test fun failedFollowupIsUnconfirmed() {
        assertEquals(decision.Outcome.UNCONFIRMED,
            decision.classify(setOf(26), null))
    }

    @Test fun noPriorOrCurrentRequestsIsUnconfirmed() {
        assertEquals(decision.Outcome.UNCONFIRMED,
            decision.classify(emptySet(), emptySet()))
    }
}
