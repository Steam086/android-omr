package com.answercard.grader.template

import org.json.JSONArray
import org.json.JSONObject

object TemplateCollectionJson {
    fun toJson(collection: TemplateCollection): String {
        val templates = JSONArray()
        collection.templates.forEach { stored ->
            templates.put(
                JSONObject()
                    .put("id", stored.id)
                    .put("template", JSONObject(TemplateJson.toJson(stored.template))),
            )
        }
        return JSONObject()
            .put("selectedTemplateId", collection.selectedTemplateId)
            .put("templates", templates)
            .toString()
    }

    fun fromJson(json: String?): TemplateCollection {
        if (json.isNullOrBlank()) return TemplateCollection.default()
        return runCatching {
            val root = JSONObject(json)
            val rawTemplates = root.getJSONArray("templates")
            val templates = (0 until rawTemplates.length()).mapNotNull { index ->
                val item = rawTemplates.getJSONObject(index)
                val id = item.optString("id").ifBlank { return@mapNotNull null }
                val template = TemplateJson.fromJson(item.optJSONObject("template")?.toString())
                StoredTemplate(id = id, template = template)
            }
            if (templates.isEmpty()) {
                TemplateCollection.default()
            } else {
                val selectedId = root.optString("selectedTemplateId", templates.first().id)
                TemplateCollection(
                    selectedTemplateId = selectedId.takeIf { id -> templates.any { it.id == id } } ?: templates.first().id,
                    templates = templates,
                )
            }
        }.getOrDefault(TemplateCollection.default())
    }
}
