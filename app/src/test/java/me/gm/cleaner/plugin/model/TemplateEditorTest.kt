package me.gm.cleaner.plugin.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateEditorTest {
    private fun template(name: String) = Template(name, listOf("query"), listOf("example.app"), null, null)

    @Test
    fun renameReplacesOnlyTheOriginalAndPreservesCurrentAssignments() {
        val current = template("old").copy(applyToApp = listOf("new.assignment"))
        val other = template("unrelated")
        val result = TemplateEditor.save(listOf(current, other), "old", template("renamed"))
        assertEquals(listOf(current.copy(templateName = "renamed"), other), result)
    }

    @Test(expected = TemplateEditor.NameConflict::class)
    fun prefilledNameCannotOverwriteAnExistingTemplate() {
        TemplateEditor.save(listOf(template("Photos")), null, template("Photos"))
    }

    @Test(expected = TemplateEditor.NameConflict::class)
    fun renameCannotOverwriteAnotherTemplate() {
        TemplateEditor.save(listOf(template("one"), template("two")), "one", template("two"))
    }

    @Test(expected = TemplateEditor.MissingTemplate::class)
    fun deletedTemplateCannotBeResurrectedByAnOldEditor() {
        TemplateEditor.save(emptyList(), "deleted", template("new"))
    }

    @Test
    fun createAppendsWithoutChangingExistingRules() {
        assertEquals(
            listOf(template("one"), template("two")),
            TemplateEditor.save(listOf(template("one")), null, template("two")),
        )
    }
}
