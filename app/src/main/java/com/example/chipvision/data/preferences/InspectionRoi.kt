package com.example.chipvision.data.preferences

data class InspectionRoi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val isValid: Boolean
        get() = listOf(left, top, right, bottom).all { java.lang.Float.isFinite(it) } &&
            left >= 0f && top >= 0f && right <= 1f && bottom <= 1f &&
            right > left && bottom > top

    companion object {
        val DEFAULT = InspectionRoi(0.10f, 0.10f, 0.90f, 0.90f)
        val FULL_FRAME = InspectionRoi(0f, 0f, 1f, 1f)

        fun validatedOrDefault(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float
        ): InspectionRoi {
            val candidate = InspectionRoi(left, top, right, bottom)
            return if (candidate.isValid) candidate else DEFAULT
        }
    }
}
