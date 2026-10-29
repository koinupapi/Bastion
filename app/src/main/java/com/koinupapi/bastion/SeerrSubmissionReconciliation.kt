package com.koinupapi.bastion

/** Pure decision logic for an ambiguous request submission.
 * Never interprets a missing or incomplete read as proof of failure.
 */
internal object SeerrSubmissionReconciliation {
    enum class Outcome { NEW_REQUEST_FOUND, EXISTING_REQUEST_FOUND, UNCONFIRMED }

    fun classify(before: Set<Int>?, after: Set<Int>?): Outcome = when {
        before != null && after != null && (after - before).isNotEmpty() ->
            Outcome.NEW_REQUEST_FOUND
        after != null && after.isNotEmpty() -> Outcome.EXISTING_REQUEST_FOUND
        else -> Outcome.UNCONFIRMED
    }
}
