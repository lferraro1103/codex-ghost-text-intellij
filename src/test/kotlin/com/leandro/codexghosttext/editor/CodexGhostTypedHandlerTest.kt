package com.leandro.codexghosttext.editor

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.leandro.codexghosttext.preview.GhostPreviewService

class CodexGhostTypedHandlerTest : LightJavaCodeInsightFixtureTestCase() {
    private val service get() = project.getService(GhostPreviewService::class.java)

    override fun tearDown() {
        service.cancel()
        super.tearDown()
    }

    fun testPipeAcceptsBeforeItCanBeInsertedIntoTheDocument() {
        myFixture.configureByText("Sample.java", "// build item\nclass Sample {}")
        val commentEnd = "// build item".length
        assertTrue(service.show(myFixture.editor, TextRange(0, commentEnd), "public void build() {}"))

        val result = CodexGhostTypedHandler().beforeCharTyped(
            '|', project, myFixture.editor, myFixture.file, myFixture.file.fileType,
        )

        assertEquals(TypedHandlerDelegate.Result.STOP, result)
        assertEquals("// build item\npublic void build() {}\nclass Sample {}", myFixture.editor.document.text)
        assertFalse(myFixture.editor.document.text.contains('|'))
    }
}
