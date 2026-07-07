package com.answercard.grader.template

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateCollectionJsonTest {
    @Test
    fun roundTripPreservesTemplatesAndSelectedTemplate() {
        val collection = TemplateCollection(
            selectedTemplateId = "science",
            templates = listOf(
                StoredTemplate(id = "default", template = TemplateState.default()),
                StoredTemplate(
                    id = "science",
                    template = TemplateState.default()
                        .withName("科学月考")
                        .withQuestionCount(16)
                        .withAnswer(2, "D"),
                ),
            ),
        )

        val restored = TemplateCollectionJson.fromJson(TemplateCollectionJson.toJson(collection))

        assertEquals(collection, restored)
    }

    @Test
    fun blankJsonFallsBackToOneDefaultTemplate() {
        val restored = TemplateCollectionJson.fromJson(null)

        assertEquals(1, restored.templates.size)
        assertEquals(restored.templates.first().id, restored.selectedTemplateId)
        assertEquals(TemplateState.default(), restored.selectedTemplate.template)
    }
}
