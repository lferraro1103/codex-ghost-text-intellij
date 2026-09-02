package com.leandro.codexghosttext.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class GenerateCodexGhostTextActionTest : LightJavaCodeInsightFixtureTestCase() {
    fun testFullLineCommentIsVisibleAndLeavesDocumentUnchanged() {
        myFixture.configureByText("Sample.java", "// create user\nclass Sample {}\n")
        val document = myFixture.editor.document
        myFixture.editor.selectionModel.setSelection(0, "// create user".length)

        val action = ActionManager.getInstance().getAction(ACTION_ID) as GenerateCodexGhostTextAction
        val event = editorEvent(action)

        action.update(event)
        assertTrue(event.presentation.isVisible)
        assertTrue(event.presentation.isEnabled)

        val before = document.text
        action.actionPerformed(editorEvent(action))
        assertEquals(before, document.text)
    }

    fun testActionIsRegisteredWithStableId() {
        val action = ActionManager.getInstance().getAction(ACTION_ID)
        assertNotNull(action)
        assertEquals("Generate Codex Ghost Text", action.templatePresentation.text)
        assertEquals(
            "Generate a Codex code proposal from the selected comment",
            action.templatePresentation.description,
        )
    }

    private fun editorEvent(action: GenerateCodexGhostTextAction): AnActionEvent {
        val context: DataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()
        return AnActionEvent(
            null,
            context,
            ActionPlaces.EDITOR_POPUP,
            action.templatePresentation.clone(),
            ActionManager.getInstance(),
            0,
        )
    }

    private companion object {
        const val ACTION_ID = "com.leandro.codexghosttext.GenerateCodexGhostText"
    }
}
