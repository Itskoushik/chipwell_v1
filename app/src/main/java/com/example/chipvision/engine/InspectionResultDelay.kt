package com.example.chipvision.engine

import com.example.chipvision.data.model.InspectionDecision

object InspectionResultDelay {
    const val PASS_DELAY_MS = 500L
    const val FAIL_DELAY_MS = 500L
    const val PASS_POPUP_DURATION_MS = 1_000L
    const val FAIL_POPUP_DURATION_MS = 1_000L

    fun forDecision(decision: InspectionDecision): Long =
        if (decision.isPass) PASS_DELAY_MS else FAIL_DELAY_MS

    fun popupDurationForDecision(decision: InspectionDecision): Long =
        if (decision.isPass) PASS_POPUP_DURATION_MS else FAIL_POPUP_DURATION_MS

    fun failureBeepIsDue(isFail: Boolean, startMs: Long, nowMs: Long): Boolean =
        isFail && nowMs < startMs + FAIL_DELAY_MS
}
