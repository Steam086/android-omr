package com.answercard.grader.miniprogram

import com.answercard.grader.template.TemplateGeometry
import kotlin.math.ceil

object AndroidPaperTemplateBuilder {
    private const val TEMPLATE_TYPE = "ANDROID_5X3"
    private const val MIN_OPTION_COUNT = 2
    private const val MAX_OPTION_COUNT = 4
    private const val MAX_QUESTION_COUNT = 60
    private const val ADMISSION_NUMBER_DIGITS = 4
    private const val DIGIT_VALUES = 10
    private const val ADMISSION_START_ROW = 0
    private const val ANSWER_START_ROW = 5

    fun build(
        questionOptionCounts: List<Int>,
        admissionNumberDigits: Int = 4,
    ): AndroidPaperTemplateLayout {
        require(questionOptionCounts.isNotEmpty()) { "questionOptionCounts must not be empty" }
        require(questionOptionCounts.size <= MAX_QUESTION_COUNT) { "questionOptionCounts must not exceed 60" }
        require(questionOptionCounts.all { it in MIN_OPTION_COUNT..MAX_OPTION_COUNT }) {
            "question option counts must be between 2 and 4"
        }
        require(admissionNumberDigits == ADMISSION_NUMBER_DIGITS) { "admissionNumberDigits must be 4" }

        val questionsPerGroup = TemplateGeometry.QUESTIONS_PER_GROUP
        val groupsPerBand = TemplateGeometry.QUESTION_GROUPS_PER_ROW
        val questionsPerBand = TemplateGeometry.QUESTIONS_PER_BAND
        val bands = ceil(questionOptionCounts.size.toDouble() / questionsPerBand.toDouble()).toInt()
        val answerRows = bands * questionsPerGroup
        val gridRows = ANSWER_START_ROW + answerRows
        val gridColumns = groupsPerBand * MAX_OPTION_COUNT
        val questionMappings = buildQuestionMappings(questionOptionCounts, questionsPerGroup, groupsPerBand)
        val admissionNumberMappings = buildAdmissionNumberMappings(admissionNumberDigits)

        return AndroidPaperTemplateLayout(
            templateType = AndroidPaperTemplateType.ANDROID_5X3,
            gridRows = gridRows,
            gridColumns = gridColumns,
            questionCount = questionOptionCounts.size,
            optionCount = questionOptionCounts.maxOrNull() ?: 0,
            admissionNumberDigits = admissionNumberDigits,
            admissionNumberArea = AndroidPaperGridArea(
                startRow = ADMISSION_START_ROW,
                endRow = admissionNumberDigits,
                startColumn = 0,
                endColumn = DIGIT_VALUES,
            ),
            answerArea = AndroidPaperGridArea(
                startRow = ANSWER_START_ROW,
                endRow = gridRows,
                startColumn = 0,
                endColumn = gridColumns,
            ),
            questionMappings = questionMappings,
            admissionNumberMappings = admissionNumberMappings,
            debugInfo = mapOf(
                "templateType" to TEMPLATE_TYPE,
                "questionCount" to questionOptionCounts.size.toString(),
                "optionCount" to (questionOptionCounts.maxOrNull() ?: 0).toString(),
                "questionsPerGroup" to questionsPerGroup.toString(),
                "groupsPerBand" to groupsPerBand.toString(),
                "questionsPerBand" to questionsPerBand.toString(),
                "grid" to "rows=$gridRows,columns=$gridColumns",
                "answerArea" to "rows=${ANSWER_START_ROW}..$gridRows,columns=0..$gridColumns",
                "admissionNumberArea" to "rows=0..$admissionNumberDigits,columns=0..$DIGIT_VALUES",
            ),
        )
    }

    private fun buildQuestionMappings(
        questionOptionCounts: List<Int>,
        questionsPerGroup: Int,
        groupsPerBand: Int,
    ): List<AndroidPaperQuestionMapping> =
        questionOptionCounts.flatMapIndexed { questionIndex, optionCount ->
            val indexInBand = questionIndex % (questionsPerGroup * groupsPerBand)
            val band = questionIndex / (questionsPerGroup * groupsPerBand)
            val group = indexInBand / questionsPerGroup
            val rowInGroup = indexInBand % questionsPerGroup
            (0 until optionCount).map { optionIndex ->
                AndroidPaperQuestionMapping(
                    questionIndex = questionIndex,
                    optionIndex = optionIndex,
                    row = ANSWER_START_ROW + band * questionsPerGroup + rowInGroup,
                    column = group * MAX_OPTION_COUNT + optionIndex,
                )
            }
        }

    private fun buildAdmissionNumberMappings(admissionNumberDigits: Int): List<AndroidPaperAdmissionNumberMapping> =
        (0 until admissionNumberDigits).flatMap { digitIndex ->
            (0 until DIGIT_VALUES).map { numberValue ->
                AndroidPaperAdmissionNumberMapping(
                    digitIndex = digitIndex,
                    numberValue = numberValue,
                    row = digitIndex,
                    column = numberValue,
                )
            }
        }
}
