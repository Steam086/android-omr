package com.answercard.grader.miniprogram

data class MiniProgramCell(
    val row: Int,
    val column: Int,
    val leftTop: MiniProgramGridPoint,
    val rightTop: MiniProgramGridPoint,
    val leftBottom: MiniProgramGridPoint,
    val rightBottom: MiniProgramGridPoint,
)
