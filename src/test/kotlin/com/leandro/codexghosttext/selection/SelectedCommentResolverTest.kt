package com.leandro.codexghosttext.selection

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.EditorFactory

class SelectedCommentResolverTest : LightJavaCodeInsightFixtureTestCase() {
    fun testAcceptsLineBlockAndDocCommentsWithExteriorWhitespace() {
        assertValid("  // line comment  \nclass Sample {}", 0, "  // line comment  \n".length)
        assertValid("\n/* block comment */\nclass Sample {}", 0, "\n/* block comment */\n".length)
        assertValid("/** doc comment */\nclass Sample {}", 0, "/** doc comment */".length)
    }

    fun testCarriesTheEditedFilesLanguageAndName() {
        myFixture.configureByText("Sample.java", "// comment\nclass Sample {}")
        myFixture.editor.selectionModel.setSelection(0, "// comment".length)

        val selected = SelectedCommentResolver.resolve(myFixture.editor, myFixture.file)

        assertEquals("Java", selected?.language)
        assertEquals("Sample.java", selected?.fileName)
    }

    fun testRejectsEmptyPartialMixedAndMultipleSelections() {
        assertInvalid("// comment\nclass Sample {}", 0, 0)
        assertInvalid("// comment\nclass Sample {}", 1, 5)
        assertInvalid("// comment\nclass Sample {}", 0, "// comment\nclass".length)
        assertInvalid("// first\n// second", 0, "// first\n// second".length)
        assertInvalid("class Sample {}", 0, "class Sample {}".length)
        assertInvalid("  \n// comment", 0, 3)
        assertInvalid("class Sample { String s = \"// comment\"; }", 27, 37)
    }

    fun testExactUtf16RangeAndEof() {
        val source = "class Sample {}\n  // generar 😀 código"
        myFixture.configureByText("Sample.java", source)
        myFixture.editor.selectionModel.setSelection(source.indexOf("  //"), source.length)
        assertEquals(TextRange(source.indexOf("//"), source.length),
            SelectedCommentResolver.resolve(myFixture.editor, myFixture.file)?.range)
        assertValid("/* EOF */", 0, 9)
    }

    fun testRejectsUncommittedPsiEvenWhenOldCommentRangeStillMatches() {
        myFixture.configureByText("Sample.java", "// comment")
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.replaceString(0, 2, "xx")
            myFixture.editor.selectionModel.setSelection(0, myFixture.editor.document.textLength)
            assertFalse(PsiDocumentManager.getInstance(project).isCommitted(myFixture.editor.document))
            assertNull(SelectedCommentResolver.resolve(myFixture.editor, myFixture.file))
        }
    }

    fun testRejectsMissingPsiContext() {
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument("// comment"))
        try {
            editor.selectionModel.setSelection(0, 10)
            val context = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                .add(CommonDataKeys.EDITOR, editor).build()
            assertNull(SelectedCommentResolver.from(AnActionEvent(null, context, ActionPlaces.EDITOR_POPUP,
                Presentation(), ActionManager.getInstance(), 0)))
        } finally { factory.releaseEditor(editor) }
    }

    fun testRejectsMalformedOffsetsWithoutReadingOutsideDocument() {
        myFixture.configureByText("Sample.java", "// comment")
        for ((start, end) in listOf(-1 to 10, 5 to 3, 0 to 11, 10 to 10, Int.MAX_VALUE to Int.MAX_VALUE)) {
            assertNull(SelectedCommentResolver.resolve(myFixture.editor.document, myFixture.file, start, end))
        }
    }

    fun testRejectsPsiFromAnotherDocument() {
        myFixture.configureByText("Sample.java", "// comment")
        val unrelated = EditorFactory.getInstance().createDocument("// comment")
        assertNull(SelectedCommentResolver.resolve(unrelated, myFixture.file, 0, 10))
    }

    private fun assertValid(source: String, start: Int, end: Int) {
        myFixture.configureByText("Sample.java", source)
        myFixture.editor.selectionModel.setSelection(start, end)
        val expectedStart = source.indexOf('/')
        val expectedEnd = if (source.startsWith("//", expectedStart)) source.indexOf('\n', expectedStart).let { if (it < 0) source.length else it }
            else source.indexOf("*/", expectedStart) + 2
        assertEquals(TextRange(expectedStart, expectedEnd), SelectedCommentResolver.resolve(myFixture.editor, myFixture.file)?.range)
    }

    private fun assertInvalid(source: String, start: Int, end: Int) {
        myFixture.configureByText("Sample.java", source)
        myFixture.editor.selectionModel.setSelection(start, end)
        assertNull(SelectedCommentResolver.resolve(myFixture.editor, myFixture.file))
    }
}
