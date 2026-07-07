package com.answercard.grader.template

data class StoredTemplate(
    val id: String,
    val template: TemplateState,
)

data class TemplateCollection(
    val selectedTemplateId: String,
    val templates: List<StoredTemplate>,
) {
    val selectedTemplate: StoredTemplate
        get() = templates.firstOrNull { it.id == selectedTemplateId } ?: templates.first()

    fun select(id: String): TemplateCollection =
        if (templates.any { it.id == id }) copy(selectedTemplateId = id) else this

    fun upsert(stored: StoredTemplate): TemplateCollection {
        val nextTemplates = if (templates.any { it.id == stored.id }) {
            templates.map { if (it.id == stored.id) stored else it }
        } else {
            templates + stored
        }
        return copy(selectedTemplateId = stored.id, templates = nextTemplates)
    }

    fun delete(id: String): TemplateCollection {
        if (templates.size <= 1) return this
        val nextTemplates = templates.filterNot { it.id == id }
        val nextSelected = if (selectedTemplateId == id) nextTemplates.first().id else selectedTemplateId
        return copy(selectedTemplateId = nextSelected, templates = nextTemplates)
    }

    companion object {
        fun default(): TemplateCollection {
            val stored = StoredTemplate(id = "default", template = TemplateState.default())
            return TemplateCollection(selectedTemplateId = stored.id, templates = listOf(stored))
        }
    }
}
