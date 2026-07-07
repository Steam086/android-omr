package com.answercard.grader.miniprogram

import com.answercard.grader.template.TemplateState

class AndroidOmrFrameProcessor(
    private val scan: (MiniProgramFrame, TemplateState) -> AndroidOmrResult = AndroidOmrEngine::scan,
) {
    fun process(
        frame: MiniProgramFrame,
        template: TemplateState,
    ): AndroidOmrResult = scan(frame, template)
}
