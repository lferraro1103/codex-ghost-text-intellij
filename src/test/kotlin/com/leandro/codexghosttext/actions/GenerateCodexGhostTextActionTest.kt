package com.leandro.codexghosttext.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionGroup
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

    fun testInvalidPopupSelectionIsHiddenAndDoesNotChangeDocument() {
        myFixture.configureByText("Sample.java", "class Sample {}\n")
        val action = ActionManager.getInstance().getAction(ACTION_ID) as GenerateCodexGhostTextAction
        val before = myFixture.editor.document.text
        val event = editorEvent(action)

        action.update(event)
        assertFalse(event.presentation.isVisible)
        action.actionPerformed(editorEvent(action))
        assertEquals(before, myFixture.editor.document.text)
        assertFalse(event.presentation.isEnabled)
    }

    fun testInvalidKeyboardInvocationEmitsExactlyOneFixedNotification() {
        myFixture.configureByText("Sample.java", "class SecretSource {}")
        myFixture.editor.selectionModel.setSelection(0, myFixture.editor.document.textLength)
        val notifications = mutableListOf<Notification>()
        project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) { notifications.add(notification) }
        })
        val action = GenerateCodexGhostTextAction()
        val event = editorEvent(action, ActionPlaces.KEYBOARD_SHORTCUT)
        val before = myFixture.editor.document.text
        action.update(event)
        assertTrue(event.presentation.isVisible)
        assertTrue(event.presentation.isEnabled)
        assertEmpty(notifications)
        action.actionPerformed(event)
        assertSize(1, notifications)
        assertEquals("Seleccioná exactamente un comentario para generar código.", notifications.single().content)
        assertEquals("Codex Ghost Text", notifications.single().groupId)
        assertEquals(NotificationType.INFORMATION, notifications.single().type)
        assertEquals(before, myFixture.editor.document.text)
        notifications.forEach { it.expire() }
    }

    fun testValidKeyboardInvocationShowsGeneratingFeedbackWithoutMutating() {
        myFixture.configureByText("Sample.java", "// create user")
        myFixture.editor.selectionModel.setSelection(0, myFixture.editor.document.textLength)
        val notifications = mutableListOf<Notification>()
        project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) { notifications.add(notification) }
        })
        val action = GenerateCodexGhostTextAction()
        val event = editorEvent(action, ActionPlaces.KEYBOARD_SHORTCUT)
        action.update(event)
        assertTrue(event.presentation.isEnabled)
        val before = myFixture.editor.document.text
        action.actionPerformed(event)
        assertSize(1, notifications)
        assertEquals("Generando propuesta con Codex…", notifications.single().content)
        assertEquals("Codex Ghost Text", notifications.single().groupId)
        assertEquals(NotificationType.INFORMATION, notifications.single().type)
        assertEquals(before, myFixture.editor.document.text)
        notifications.forEach { it.expire() }
    }

    fun testMissingEditorDisablesKeyboardActionAndHidesPopup() {
        val action = GenerateCodexGhostTextAction()
        for (place in listOf(ActionPlaces.KEYBOARD_SHORTCUT, ActionPlaces.EDITOR_POPUP)) {
            val event = AnActionEvent(null, DataContext.EMPTY_CONTEXT, place,
                action.templatePresentation.clone(), ActionManager.getInstance(), 0)
            action.update(event)
            assertFalse(event.presentation.isEnabled)
            assertEquals(place != ActionPlaces.EDITOR_POPUP, event.presentation.isVisible)
            action.actionPerformed(event)
        }
    }

    fun testActionIsDirectPopupChildWithoutDefaultShortcut() {
        val manager = ActionManager.getInstance()
        val action = manager.getAction(ACTION_ID)
        val popup = manager.getAction("EditorPopupMenu") as ActionGroup
        assertTrue(popup.getChildren(null).contains(action))
        assertEmpty(action.shortcutSet.shortcuts)
    }

    private fun editorEvent(action: GenerateCodexGhostTextAction, place: String = ActionPlaces.EDITOR_POPUP): AnActionEvent {
        val context: DataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()
        return AnActionEvent(
            null,
            context,
            place,
            action.templatePresentation.clone(),
            ActionManager.getInstance(),
            0,
        )
    }

    private companion object {
        const val ACTION_ID = "com.leandro.codexghosttext.GenerateCodexGhostText"
    }
}
