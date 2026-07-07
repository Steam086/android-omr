package com.answercard.grader.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.answercard.grader.template.TemplateRenderer
import com.answercard.grader.template.TemplateState
import java.io.File
import java.time.LocalDateTime

object PngShare {
    private const val MIME_TYPE = "image/png"
    private const val AUTHORITY = "com.answercard.grader.fileprovider"

    fun createShareIntent(
        context: Context,
        templateName: String,
        timestamp: LocalDateTime = LocalDateTime.now(),
        scale: Float = 3f,
    ): Intent = createShareIntent(context, TemplateState.default().copy(name = templateName), timestamp, scale)

    fun createShareIntent(
        context: Context,
        template: TemplateState,
        timestamp: LocalDateTime = LocalDateTime.now(),
        scale: Float = 3f,
    ): Intent {
        val uri = writeCachePng(context, template, timestamp, scale)
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writeCachePng(
        context: Context,
        template: TemplateState,
        timestamp: LocalDateTime,
        scale: Float,
    ): Uri {
        val shareDir = File(context.cacheDir, "shared-png").apply { mkdirs() }
        val file = File(shareDir, PngFileName.forTemplate(template.name, timestamp))
        file.writeBytes(TemplateRenderer.renderPng(template, scale = scale))
        return FileProvider.getUriForFile(context, AUTHORITY, file)
    }
}
