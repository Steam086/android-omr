package com.answercard.grader.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.answercard.grader.template.TemplateRenderer
import com.answercard.grader.template.TemplateState
import java.io.File
import java.time.LocalDateTime

object PngExporter {
    private const val MIME_TYPE = "image/png"
    private const val PICTURES_DIR = "Pictures/答题卡阅卷"

    fun saveTemplatePng(
        context: Context,
        templateName: String,
        timestamp: LocalDateTime = LocalDateTime.now(),
        scale: Float = 3f,
    ): Uri = saveTemplatePng(context, TemplateState.default().copy(name = templateName), timestamp, scale)

    fun saveTemplatePng(
        context: Context,
        template: TemplateState,
        timestamp: LocalDateTime = LocalDateTime.now(),
        scale: Float = 3f,
    ): Uri {
        val templateName = template.name
        val fileName = PngFileName.forTemplate(templateName, timestamp)
        val png = TemplateRenderer.renderPng(template, scale = scale)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, PICTURES_DIR)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return saveToAppPictures(context, fileName, png)
        }

        val resolver = context.contentResolver
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        ) { "Could not create image entry" }

        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(png)
            } ?: error("Could not open image output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        }.getOrElse { error ->
            resolver.delete(uri, null, null)
            throw error
        }

        return uri
    }

    private fun saveToAppPictures(context: Context, fileName: String, png: ByteArray): Uri {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "答题卡阅卷")
            .apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(png)
        return Uri.fromFile(file)
    }
}
