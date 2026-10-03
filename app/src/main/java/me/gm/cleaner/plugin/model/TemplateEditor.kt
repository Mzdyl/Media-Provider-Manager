package me.gm.cleaner.plugin.model

/** The original name identifies an edit; a prefilled name is still a new template. */
object TemplateEditor {
    class NameConflict : IllegalArgumentException()
    class MissingTemplate : IllegalStateException()

    fun save(existing: List<Template>, originalName: String?, draft: Template): List<Template> {
        require(draft.templateName.isNotBlank() && draft.hookOperation.isNotEmpty())
        if (existing.any { it.templateName == draft.templateName && it.templateName != originalName }) {
            throw NameConflict()
        }
        if (originalName == null) return existing + draft
        val original = existing.firstOrNull { it.templateName == originalName }
            ?: throw MissingTemplate()
        // App assignments may have changed since the editor was opened.
        return existing.map {
            if (it.templateName == originalName) draft.copy(applyToApp = original.applyToApp) else it
        }
    }
}
