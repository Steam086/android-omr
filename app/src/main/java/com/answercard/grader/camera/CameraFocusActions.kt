package com.answercard.grader.camera

import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import java.util.concurrent.TimeUnit

data class CameraFocusActionSpec(
    val flags: Int,
    val autoCancelSeconds: Long,
)

object CameraFocusActions {
    val DefaultSpec = CameraFocusActionSpec(
        flags = FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        autoCancelSeconds = 3L,
    )

    fun actionFor(
        point: MeteringPoint,
        spec: CameraFocusActionSpec = DefaultSpec,
    ): FocusMeteringAction =
        FocusMeteringAction.Builder(point, spec.flags)
            .setAutoCancelDuration(spec.autoCancelSeconds, TimeUnit.SECONDS)
            .build()
}
