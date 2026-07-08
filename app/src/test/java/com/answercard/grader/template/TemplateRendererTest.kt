package com.answercard.grader.template

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TemplateRendererTest {
    @Test
    fun renderIncludesCornerAnchorMargin() {
        val bitmap = TemplateRenderer.render(TemplateGeometry.buildLayout(), scale = 3f)

        assertEquals(1860, bitmap.width)
        assertEquals(888, bitmap.height)
    }

    @Test
    fun renderPngReturnsPngBytes() {
        val png = TemplateRenderer.renderPng(TemplateGeometry.buildLayout(), scale = 1f)

        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), png.take(4).toByteArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun renderRejectsZeroScale() {
        TemplateRenderer.render(TemplateGeometry.buildLayout(), scale = 0f)
    }

    @Test
    fun renderTemplateUsesTemplateDrivenLayout() {
        val template = TemplateState.default().withQuestionOptions(question = 1, optionCount = 2)
        val expected = TemplateRenderer.renderPng(TemplateGeometry.buildLayout(template), scale = 1f)
        val actual = TemplateRenderer.renderPng(template, scale = 1f)

        assertArrayEquals(expected, actual)
    }

    @Test
    fun headerlessTemplateRendersCompactCard() {
        val header = TemplateRenderer.render(TemplateState.default(), scale = 3f)
        val headerless = TemplateRenderer.render(TemplateState.default().withShowHeader(false), scale = 3f)

        assertEquals(header.width, headerless.width)
        assertEquals(576, headerless.height)
    }
}
