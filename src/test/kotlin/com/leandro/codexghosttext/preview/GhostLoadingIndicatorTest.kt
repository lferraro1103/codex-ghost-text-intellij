package com.leandro.codexghosttext.preview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class GhostLoadingIndicatorTest : LightJavaCodeInsightFixtureTestCase() {
    private val indicator get() = project.getService(GhostLoadingIndicator::class.java)

    override fun tearDown() {
        indicator.hide()
        super.tearDown()
    }

    fun testPlaceholderIsVisibleUnderTheCommentWithoutTouchingTheDocument() {
        val source = "// build item\nclass Sample {}\n"
        myFixture.configureByText("Sample.java", source)
        val comment = TextRange(0, "// build item".length)
        val document = myFixture.editor.document
        val stamp = document.modificationStamp

        assertTrue(indicator.show(myFixture.editor, comment))

        assertTrue(indicator.isVisible())
        assertEquals(source, document.text)
        assertEquals(stamp, document.modificationStamp)
        val inlays = myFixture.editor.inlayModel.getBlockElementsInRange(0, document.textLength)
        assertEquals(1, inlays.count { it.renderer is GhostSkeletonRenderer })
    }

    fun testPlaceholderDisappearsOnEditAndOnHide() {
        myFixture.configureByText("Sample.java", "// build item\nclass Sample {}")
        assertTrue(indicator.show(myFixture.editor, TextRange(0, "// build item".length)))

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(myFixture.editor.document.textLength, "\n// changed")
        }

        assertFalse(indicator.isVisible())
        assertFalse(indicator.hide())
    }

    fun testShowingAgainReplacesThePreviousPlaceholder() {
        myFixture.configureByText("Sample.java", "// build item\nclass Sample {}")
        val comment = TextRange(0, "// build item".length)

        assertTrue(indicator.show(myFixture.editor, comment))
        assertTrue(indicator.show(myFixture.editor, comment))

        val document = myFixture.editor.document
        val inlays = myFixture.editor.inlayModel.getBlockElementsInRange(0, document.textLength)
        assertEquals(1, inlays.count { it.renderer is GhostSkeletonRenderer })
    }
}
