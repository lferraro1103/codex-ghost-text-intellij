package com.leandro.codexghosttext.selection

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class SelectedCommentResolverTest : LightJavaCodeInsightFixtureTestCase() {
    fun testAcceptsLineBlockAndDocCommentsWithExteriorWhitespace() {
        assertValid("  // line comment  \nclass Sample {}", 0, "  // line comment  \n".length)
        assertValid("\n/* block comment */\nclass Sample {}", 0, "\n/* block comment */\n".length)
        assertValid("/** doc comment */\nclass Sample {}", 0, "/** doc comment */".length)
    }

    fun testRejectsEmptyPartialMixedAndMultipleSelections() {
        assertInvalid("// comment\nclass Sample {}", 0, 0)
        assertInvalid("// comment\nclass Sample {}", 1, 5)
        assertInvalid("// comment\nclass Sample {}", 0, "// comment\nclass".length)
        assertInvalid("// first\n// second", 0, "// first\n// second".length)
        assertInvalid("class Sample {}", 0, "class Sample {}".length)
    }

    private fun assertValid(source: String, start: Int, end: Int) {
        myFixture.configureByText("Sample.java", source)
        myFixture.editor.selectionModel.setSelection(start, end)
        assertNotNull(SelectedCommentResolver.resolve(myFixture.editor, myFixture.file))
    }

    private fun assertInvalid(source: String, start: Int, end: Int) {
        myFixture.configureByText("Sample.java", source)
        myFixture.editor.selectionModel.setSelection(start, end)
        assertNull(SelectedCommentResolver.resolve(myFixture.editor, myFixture.file))
    }
}
