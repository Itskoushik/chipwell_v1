package com.example.chipvision.engine

import com.example.chipvision.data.model.InspectionDecision

object InspectionResultDelay {
    const val PASS_DELAY_MS = 1_000L
    const val FAIL_DELAY_MS = 5_000L

    fun forDecision(decision: InspectionDecision): Long =
        if (decision.isPass) PASS_DELAY_MS else FAIL_DELAY_MS

    fun failureBeepIsDue(isFail: Boolean, startMs: Long, nowMs: Long): Boolean =
        isFail && nowMs < startMs + FAIL_DELAY_MS
}
