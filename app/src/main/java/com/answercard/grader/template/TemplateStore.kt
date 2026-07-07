package com.answercard.grader.template

import android.content.Context

class TemplateStore(context: Context) {
    private val preferences = context.getSharedPreferences("templates", Context.MODE_PRIVATE)

    fun loadCollection(): TemplateCollection {
        val collectionJson = preferences.getString(KEY_TEMPLATE_COLLECTION, null)
        if (!collectionJson.isNullOrBlank()) return TemplateCollectionJson.fromJson(collectionJson)

        val legacyTemplate = TemplateJson.fromJson(preferences.getString(KEY_DEFAULT_TEMPLATE, null))
        return TemplateCollection.default().upsert(
            StoredTemplate(id = "default", template = legacyTemplate),
        )
    }

    fun saveCollection(collection: TemplateCollection) {
        preferences.edit()
            .putString(KEY_TEMPLATE_COLLECTION, TemplateCollectionJson.toJson(collection))
            .apply()
    }

    fun loadDefaultTemplate(): TemplateState =
        loadCollection().selectedTemplate.template

    fun saveDefaultTemplate(template: TemplateState) {
        val collection = loadCollection()
        saveCollection(
            collection.upsert(
                StoredTemplate(id = collection.selectedTemplate.id, template = template),
            ),
        )
    }

    private companion object {
        const val KEY_DEFAULT_TEMPLATE = "default_template"
        const val KEY_TEMPLATE_COLLECTION = "template_collection"
    }
}
