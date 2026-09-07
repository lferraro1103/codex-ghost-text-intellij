package com.leandro.codexghosttext.preview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class GhostPreviewServiceTest : LightJavaCodeInsightFixtureTestCase() {
    private val service get() = project.getService(GhostPreviewService::class.java)

    override fun tearDown() {
        service.cancel()
        super.tearDown()
    }

    fun testPreviewDoesNotMutateAndFreshAcceptancePreservesComment() {
        val source = "// build item\nclass Sample {}\n"
        myFixture.configureByText("Sample.java", source)
        myFixture.editor.selectionModel.setSelection(0, "// build item".length)
        val document = myFixture.editor.document
        val stamp = document.modificationStamp

        assertTrue(service.show(myFixture.editor, TextRange(0, "// build item".length), "val item = 1"))
        assertEquals(source, document.text)
        assertEquals(stamp, document.modificationStamp)
        assertTrue(service.acceptIfFresh(myFixture.editor))
        assertEquals("// build item\nval item = 1\nclass Sample {}\n", document.text)
        assertFalse(service.acceptIfFresh(myFixture.editor))
    }

    fun testEditCancelsAndCannotInsertStalePreview() {
        myFixture.configureByText("Sample.java", "// build item\nclass Sample {}")
        myFixture.editor.selectionModel.setSelection(0, "// build item".length)
        assertTrue(service.show(myFixture.editor, TextRange(0, "// build item".length), "val item = 1"))
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(myFixture.editor.document.textLength, "\n// changed")
        }
        assertFalse(service.acceptIfFresh(myFixture.editor))
        assertFalse(myFixture.editor.document.text.contains("val item = 1"))
    }

    fun testNavigationAndSelectionChangesKeepPreviewAcceptable() {
        myFixture.configureByText("Sample.java", "// build item\nclass Sample {}")
        myFixture.editor.selectionModel.setSelection(0, "// build item".length)
        assertTrue(service.show(myFixture.editor, TextRange(0, "// build item".length), "val item = 1"))

        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
        myFixture.editor.selectionModel.removeSelection()

        assertTrue(service.acceptIfFresh(myFixture.editor))
        assertEquals("// build item\nval item = 1\nclass Sample {}", myFixture.editor.document.text)
    }

    fun testRejectsUnsafeInlineCommentAndOversizedProposal() {
        myFixture.configureByText("Sample.java", "/* explain */ class Sample {}")
        myFixture.editor.selectionModel.setSelection(0, "/* explain */".length)
        assertFalse(service.show(myFixture.editor, TextRange(0, "/* explain */".length), "val item = 1"))

        myFixture.configureByText("Other.java", "// explain")
        myFixture.editor.selectionModel.setSelection(0, "// explain".length)
        assertFalse(service.show(myFixture.editor, TextRange(0, "// explain".length), "x".repeat(GhostPreviewService.MAX_CHARACTERS + 1)))
    }
}
