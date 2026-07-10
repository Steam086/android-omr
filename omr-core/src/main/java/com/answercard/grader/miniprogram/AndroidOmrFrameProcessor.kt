package com.answercard.grader.miniprogram

import com.answercard.grader.template.TemplateState

class AndroidOmrFrameProcessor(
    anchorMode: AnchorMode = AnchorMode.CODED_ONLY,
    private val scan: (MiniProgramFrame, TemplateState) -> AndroidOmrResult = { frame, template ->
        AndroidOmrEngine.scan(frame, template, anchorMode)
    },
) {
    fun process(
        frame: MiniProgramFrame,
        template: TemplateState,
    ): AndroidOmrResult = scan(frame, template)
}
