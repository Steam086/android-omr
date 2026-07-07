package com.answercard.grader.cli

import com.answercard.grader.miniprogram.AndroidOmrEngine
import com.answercard.grader.miniprogram.MiniProgramFrame
import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

private const val DEFAULT_QUESTION_COUNT = 16
private const val DEFAULT_SCORE = 2

fun main(args: Array<String>) {
    val options = try {
        CliOptions.parse(args.toList())
    } catch (error: IllegalArgumentException) {
        System.err.println(error.message)
        printUsage()
        exitProcess(1)
    }

    if (options.help) {
        printUsage()
        return
    }

    val input = options.inputFile ?: run {
        System.err.println("Missing image path.")
        printUsage()
        exitProcess(1)
    }
    if (!input.isFile) {
        System.err.println("Image file not found: ${input.path}")
        exitProcess(1)
    }

    val template = buildTemplate(options)
    val frame = DesktopImageLoader.load(input)
    val result = AndroidOmrEngine.scan(frame = frame, template = template)

    println("Image: ${input.path}")
    println("Frame: ${frame.width}x${frame.height}")
    println("Questions: ${template.questions.size}")
    println("Success: ${result.success}")
    result.failureReason?.let { println("Failure: $it") }
    println("Admission: ${result.admissionNumber?.digits ?: "-"}")
    result.score?.let { score ->
        println("Score: ${score.totalScore}/${score.maxScore}")
    }
    if (result.warnings.isNotEmpty()) {
        println("Warnings:")
        result.warnings.forEach { println("  $it") }
    }
    result.answerArea?.questions.orEmpty().forEach { question ->
        val labels = if (question.selectedLabels.isEmpty()) "-" else question.selectedLabels.joinToString("")
        println("Q${question.questionIndex + 1}: $labels")
    }
    if (options.debug) {
        println("Debug:")
        result.debugInfo.forEach { println("  $it") }
    }

    if (!result.success) exitProcess(2)
}

private fun buildTemplate(options: CliOptions): TemplateState {
    val explicitAnswers = options.answers
    val questions = (1..options.questionCount).map { number ->
        val answer = explicitAnswers[number] ?: "A"
        val score = if (explicitAnswers.isEmpty() || explicitAnswers.containsKey(number)) {
            options.score
        } else {
            0
        }
        QuestionSetting(number = number, answer = answer, score = score)
    }
    return TemplateState(name = "desktop-cli", questions = questions)
}

private data class CliOptions(
    val inputFile: File?,
    val questionCount: Int,
    val answers: Map<Int, String>,
    val score: Int,
    val debug: Boolean,
    val help: Boolean,
) {
    companion object {
        fun parse(args: List<String>): CliOptions {
            var inputFile: File? = null
            var questionCount = DEFAULT_QUESTION_COUNT
            var answers = emptyMap<Int, String>()
            var score = DEFAULT_SCORE
            var debug = false
            var help = false

            var index = 0
            while (index < args.size) {
                val arg = args[index]
                when {
                    arg == "--help" || arg == "-h" -> help = true
                    arg == "--debug" -> debug = true
                    arg == "--questions" -> {
                        questionCount = args.valueAfter(index, arg).toIntStrict(arg)
                        index += 1
                    }
                    arg.startsWith("--questions=") -> {
                        questionCount = arg.substringAfter("=").toIntStrict("--questions")
                    }
                    arg == "--score" -> {
                        score = args.valueAfter(index, arg).toIntStrict(arg)
                        index += 1
                    }
                    arg.startsWith("--score=") -> {
                        score = arg.substringAfter("=").toIntStrict("--score")
                    }
                    arg == "--answers" -> {
                        answers = parseAnswers(args.valueAfter(index, arg))
                        index += 1
                    }
                    arg.startsWith("--answers=") -> {
                        answers = parseAnswers(arg.substringAfter("="))
                    }
                    arg.startsWith("-") -> throw IllegalArgumentException("Unknown option: $arg")
                    inputFile == null -> inputFile = File(arg)
                    else -> throw IllegalArgumentException("Unexpected argument: $arg")
                }
                index += 1
            }

            require(questionCount in 1..60) { "--questions must be in 1..60" }
            require(score >= 0) { "--score must be non-negative" }
            val outside = answers.keys.filter { it !in 1..questionCount }
            require(outside.isEmpty()) {
                "--answers contains question numbers outside 1..$questionCount: ${outside.joinToString()}"
            }

            return CliOptions(
                inputFile = inputFile,
                questionCount = questionCount,
                answers = answers,
                score = score,
                debug = debug,
                help = help,
            )
        }
    }
}

private fun List<String>.valueAfter(index: Int, option: String): String {
    val value = getOrNull(index + 1)
    require(value != null && !value.startsWith("-")) { "$option requires a value" }
    return value
}

private fun String.toIntStrict(option: String): Int =
    toIntOrNull() ?: throw IllegalArgumentException("$option requires an integer value")

private fun parseAnswers(value: String): Map<Int, String> {
    if (value.isBlank()) return emptyMap()
    return value.split(",")
        .filter { it.isNotBlank() }
        .associate { token ->
            val parts = token.split(":")
            require(parts.size == 2) { "--answers entries must use N:LABEL format, got: $token" }
            val number = parts[0].trim().toIntStrict("--answers")
            val answer = parts[1].trim().uppercase()
            require(answer.isNotEmpty()) { "--answers contains an empty answer for question $number" }
            require(answer.all { it in 'A'..'D' }) {
                "--answers supports A-D labels, got: $answer"
            }
            number to answer
        }
}

private object DesktopImageLoader {
    fun load(file: File): MiniProgramFrame {
        val image = ImageIO.read(file) ?: error("Unsupported image file: ${file.path}")
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        for (row in 0 until height) {
            for (column in 0 until width) {
                val rgb = image.getRGB(column, row)
                val red = rgb ushr 16 and 0xff
                val green = rgb ushr 8 and 0xff
                val blue = rgb and 0xff
                pixels[row * width + column] = (red * 299 + green * 587 + blue * 114) / 1000
            }
        }
        return MiniProgramFrame(width = width, height = height, pixels = pixels)
    }
}

private fun printUsage() {
    println(
        """
        Usage:
          gradlew :omr-cli:run --args="<image> [--questions N] [--answers 1:A,2:B] [--score N] [--debug]"

        Defaults:
          --questions $DEFAULT_QUESTION_COUNT
          --score $DEFAULT_SCORE

        When --answers is provided, only listed questions receive --score; unlisted questions use score 0.
        """.trimIndent(),
    )
}
