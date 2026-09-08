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
import com.intellij.openapi.util.TextRange
import com.leandro.codexghosttext.generation.GenerationRequest
import com.leandro.codexghosttext.generation.GenerationResult
import com.leandro.codexghosttext.generation.LocalGenerationProvider
import com.leandro.codexghosttext.generation.ProviderDiagnostic
import com.leandro.codexghosttext.generation.ProviderId
import com.leandro.codexghosttext.provider.ProviderSelectionSnapshot
import com.leandro.codexghosttext.status.GenerationRequestTracker
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

    fun testValidKeyboardInvocationDoesNotShowABalloonOrMutate() {
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
        assertEmpty(notifications)
        assertEquals(before, myFixture.editor.document.text)
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

    fun testDispatchCallsOnlyTheProviderCapturedAtInvocation() {
        val codex = RecordingProvider(ProviderId.CODEX, GenerationResult.Success("public void codex() {}"))
        val claude = RecordingProvider(ProviderId.CLAUDE, GenerationResult.Success("public void claude() {}"))
        val tracker = ActionGenerationRequestTracker()
        val dispatch = tracker.capture(ProviderSelectionSnapshot(ProviderId.CLAUDE, claude, 7)) { true }

        val result = dispatch.generate(request())

        assertEquals(GenerationResult.Success("public void claude() {}"), result)
        assertEquals(0, codex.generateCalls)
        assertEquals(1, claude.generateCalls)
        assertTrue(dispatch.isCurrent())
    }

    fun testProviderFailureDoesNotFallBackToTheOtherProvider() {
        val codex = RecordingProvider(ProviderId.CODEX, GenerationResult.Failure("Codex no está listo"))
        val claude = RecordingProvider(ProviderId.CLAUDE, GenerationResult.Success("public void claude() {}"))
        val dispatch = ActionGenerationRequestTracker()
            .capture(ProviderSelectionSnapshot(ProviderId.CODEX, codex, 3)) { true }

        assertEquals(GenerationResult.Failure("Codex no está listo"), dispatch.generate(request()))
        assertEquals(1, codex.generateCalls)
        assertEquals(0, claude.generateCalls)
    }

    fun testSwitchOrNewRequestMakesLateResultUndeliverable() {
        val claude = RecordingProvider(ProviderId.CLAUDE, GenerationResult.Success("public void claude() {}"))
        var providerStillSelected = true
        val tracker = ActionGenerationRequestTracker()
        val first = tracker.capture(ProviderSelectionSnapshot(ProviderId.CLAUDE, claude, 4)) { providerStillSelected }

        assertEquals(GenerationResult.Success("public void claude() {}"), first.generate(request()))
        providerStillSelected = false
        assertFalse(first.isCurrent())

        providerStillSelected = true
        val second = tracker.capture(ProviderSelectionSnapshot(ProviderId.CLAUDE, claude, 4)) { providerStillSelected }
        assertFalse(first.isCurrent())
        assertTrue(second.isCurrent())
    }

    fun testStaleDispatchDoesNotStartAnotherProviderRequest() {
        val codex = RecordingProvider(ProviderId.CODEX, GenerationResult.Success("public void codex() {}"))
        val tracker = ActionGenerationRequestTracker()
        var providerStillSelected = false
        val dispatch = tracker.capture(ProviderSelectionSnapshot(ProviderId.CODEX, codex, 1)) { providerStillSelected }

        assertEquals(GenerationResult.Failure(ProviderDiagnostic.CANCELLED.userMessage), dispatch.generate(request()))
        assertEquals(0, codex.generateCalls)

        providerStillSelected = true
        assertTrue(tracker.capture(ProviderSelectionSnapshot(ProviderId.CODEX, codex, 1)) { providerStillSelected }.isCurrent())
    }

    fun testOlderActionCompletionCannotClearTheNewerProviderSpinner() {
        val spinner = GenerationRequestTracker()
        val older = spinner.begin()
        val newer = spinner.begin()

        assertFalse(spinner.finish(older))
        assertEquals(newer, spinner.activeRequest())
        assertTrue(spinner.finish(newer))
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

    private fun request() = GenerationRequest(
        comment = "// create method",
        documentText = "// create method\nclass Sample {}",
        range = TextRange(0, "// create method".length),
        projectRoot = "test-project",
    )

    private class RecordingProvider(
        override val providerId: ProviderId,
        private val result: GenerationResult,
    ) : LocalGenerationProvider {
        var generateCalls = 0

        override fun checkAvailability(): ProviderDiagnostic = ProviderDiagnostic.READY

        override fun generate(request: GenerationRequest): GenerationResult {
            generateCalls++
            return result
        }

        override fun cancel() = Unit

        override fun isGenerating(): Boolean = false

        override fun resetConversation() = Unit
    }
}
