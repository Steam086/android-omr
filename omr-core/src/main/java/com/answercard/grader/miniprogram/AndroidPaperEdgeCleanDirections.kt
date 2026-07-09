package com.answercard.grader.miniprogram

object AndroidPaperEdgeCleanDirections {
    fun forQuestion(
        mapping: AndroidPaperQuestionMapping,
        layout: AndroidPaperTemplateLayout,
    ): Set<MiniProgramEdgeCleanDirection> {
        val questionMappings = layout.questionMappings.filter { it.questionIndex == mapping.questionIndex }
        val columns = questionMappings.map { it.column }
        val directions = mutableSetOf<MiniProgramEdgeCleanDirection>()
        if (columns.isNotEmpty() && mapping.column == columns.minOrNull()) {
            directions += MiniProgramEdgeCleanDirection.LEFT
        }
        if (columns.isNotEmpty() && mapping.column == columns.maxOrNull()) {
            directions += MiniProgramEdgeCleanDirection.RIGHT
        }
        if (mapping.row == layout.answerArea.startRow) {
            directions += MiniProgramEdgeCleanDirection.UP
        }
        if (mapping.row == layout.answerArea.endRow - 1) {
            directions += MiniProgramEdgeCleanDirection.DOWN
        }
        return directions
    }

    fun forAdmission(
        mapping: AndroidPaperAdmissionNumberMapping,
        layout: AndroidPaperTemplateLayout,
    ): Set<MiniProgramEdgeCleanDirection> {
        val directions = mutableSetOf<MiniProgramEdgeCleanDirection>()
        if (mapping.numberValue == layout.admissionNumberArea.startColumn) {
            directions += MiniProgramEdgeCleanDirection.LEFT
        }
        if (mapping.numberValue == layout.admissionNumberArea.endColumn - 1) {
            directions += MiniProgramEdgeCleanDirection.RIGHT
        }
        directions += MiniProgramEdgeCleanDirection.UP
        directions += MiniProgramEdgeCleanDirection.DOWN
        return directions
    }
}
