package com.answercard.grader.camera

import androidx.camera.core.FocusMeteringAction
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFocusActionsTest {
    @Test
    fun defaultFocusSpecUsesAfAeAndThreeSecondAutoCancel() {
        assertEquals(
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
            CameraFocusActions.DefaultSpec.flags,
        )
        assertEquals(3L, CameraFocusActions.DefaultSpec.autoCancelSeconds)
    }
}
