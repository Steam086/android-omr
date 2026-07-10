package com.answercard.grader.miniprogram

object CodedCardFramePolicy {
    fun requiresAnchorBorderInset(
        anchorMode: AnchorMode,
        inferredCodedMarkerCount: Int,
    ): Boolean = anchorMode != AnchorMode.CODED_ONLY || inferredCodedMarkerCount != 1
}
