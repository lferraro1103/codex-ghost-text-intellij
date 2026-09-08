package com.leandro.codexghosttext.integration

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.leandro.codexghosttext.claude.ClaudeCommandResult
import com.leandro.codexghosttext.claude.ClaudeGenerationService
import com.leandro.codexghosttext.claude.ClaudeProcessRunner
import com.leandro.codexghosttext.claude.ClaudeProjectConversationState
import com.leandro.codexghosttext.claude.DefaultClaudeProcessRunner
import com.leandro.codexghosttext.claude.RecordingClaudeCommandExecutor
import com.leandro.codexghosttext.claude.StaticClaudeExecutableLocator
import com.leandro.codexghosttext.editor.CodexGhostTypedHandler
import com.leandro.codexghosttext.generation.GenerationRequest
import com.leandro.codexghosttext.generation.GenerationResult
import com.leandro.codexghosttext.generation.LocalGenerationProvider
import com.leandro.codexghosttext.generation.ProviderDiagnostic
import com.leandro.codexghosttext.generation.ProviderId
import com.leandro.codexghosttext.preview.GhostPreviewService
import com.leandro.codexghosttext.provider.ProviderProjectState
import com.leandro.codexghosttext.provider.ProviderRouterService
import java.nio.file.Path

class MultiProviderWorkflowTest : LightJavaCodeInsightFixtureTestCase() {
    private val preview get() = project.getService(GhostPreviewService::class.java)

    override fun tearDown() {
        preview.cancel()
        super.tearDown()
    }

    fun testSwitchingProvidersCancelsPreviewRejectsLateCodexAndAcceptsClaudeWithPipe() {
        val source = "class Tree {\n    // extract root\n}\n"
        myFixture.configureByText("Tree.java", source)
        val commentStart = source.indexOf("// extract root")
        val commentRange = TextRange(commentStart, commentStart + "// extract root".length)
        val codex = RecordingProvider(ProviderId.CODEX, GenerationResult.Success("public Node extractRoot() { return root; }"))
        val claude = RecordingProvider(ProviderId.CLAUDE, GenerationResult.Success("public Node extractRoot() {\n    Node result = root;\n    root = null;\n    return result;\n}"))
        val router = router(codex, claude)
        val codexSnapshot = router.snapshot()

        assertTrue(preview.show(myFixture.editor, commentRange, "public Node stale() { return root; }"))
        assertEquals(source, myFixture.editor.document.text)
        assertTrue(router.select(ProviderId.CLAUDE))
        assertFalse(router.isCurrent(codexSnapshot))
        assertFalse(preview.acceptIfFresh(myFixture.editor))

        val request = GenerationRequest("// extract root", source, commentRange, "C:/private/project")
        val result = router.generate(request)
        assertEquals(GenerationResult.Success(claude.code), result)
        assertEquals(0, codex.generateCalls)
        assertEquals(1, claude.generateCalls)
        assertTrue(preview.show(myFixture.editor, commentRange, claude.code))

        val typed = CodexGhostTypedHandler().beforeCharTyped(
            '|', project, myFixture.editor, myFixture.file, myFixture.file.fileType,
        )
        assertEquals(TypedHandlerDelegate.Result.STOP, typed)
        assertEquals(
            "class Tree {\n    // extract root\n    public Node extractRoot() {\n        Node result = root;\n        root = null;\n        return result;\n    }\n}\n",
            myFixture.editor.document.text,
        )
    }

    fun testProviderSessionsResetIndependentlyAndUnavailableClaudeNeverFallsBack() {
        val codex = RecordingProvider(ProviderId.CODEX, GenerationResult.Success("fun fromCodex() = Unit"), session = "codex-thread")
        val claude = RecordingProvider(ProviderId.CLAUDE, GenerationResult.Failure(ProviderDiagnostic.UNSAFE_CAPABILITIES.userMessage), session = "claude-session")
        val router = router(codex, claude)
        val request = GenerationRequest("// generate", "// generate", TextRange(0, 11), "C:/private/project")

        router.select(ProviderId.CLAUDE)
        assertEquals(ProviderDiagnostic.UNSAFE_CAPABILITIES, router.checkAvailability())
        assertEquals(GenerationResult.Failure(ProviderDiagnostic.UNSAFE_CAPABILITIES.userMessage), router.generate(request))
        assertEquals(0, codex.generateCalls)
        assertEquals(1, claude.generateCalls)
        assertEquals(ProviderId.CLAUDE, router.resetSelectedConversation())
        assertEquals("codex-thread", codex.session)
        assertEquals(null, claude.session)

        router.select(ProviderId.CODEX)
        assertEquals(GenerationResult.Success("fun fromCodex() = Unit"), router.generate(request))
        assertEquals("codex-thread", codex.session)
        assertEquals(1, codex.generateCalls)
    }

    fun testClaudeWorkflowUsesOnlyBoundedEditorPromptAndToolFreeNeutralProcess() {
        val projectRoot = "C:/very/private/intellij-project"
        val executor = RecordingClaudeCommandExecutor(
            ClaudeCommandResult(stdout = "2.1.259"),
            ClaudeCommandResult(stdout = ClaudeProcessRunner.requiredCapabilityFlags.joinToString(" ")),
            ClaudeCommandResult(stdout = "{\"authenticated\":true}"),
            ClaudeCommandResult(
                stdout = "{\"session_id\":\"claude-isolated-session\",\"structured_output\":{\"code\":\"fun generated() = Unit\"}}",
            ),
        )
        val neutralDirectory = Path.of("C:/plugin-neutral-process-directory")
        val claude = ClaudeGenerationService(
            DefaultClaudeProcessRunner(
                StaticClaudeExecutableLocator(Path.of("C:/tools/claude.exe")),
                executor,
                neutralDirectory,
            ),
            ClaudeProjectConversationState(),
        )
        val codex = RecordingProvider(ProviderId.CODEX, GenerationResult.Success("fun shouldNeverRun() = Unit"))
        val router = ProviderRouterService(
            ProviderProjectState(),
            mapOf(ProviderId.CODEX to codex, ProviderId.CLAUDE to claude),
            cancelPreview = { preview.cancel() },
            notifySelection = {},
        )
        val comment = "// generate only a method"
        val document = "before-editor-context\n$comment\nafter-editor-context"
        val range = TextRange("before-editor-context\n".length, "before-editor-context\n$comment".length)

        router.select(ProviderId.CLAUDE)
        assertEquals(
            GenerationResult.Success("fun generated() = Unit"),
            router.generate(GenerationRequest(comment, document, range, projectRoot)),
        )
        assertEquals(0, codex.generateCalls)
        val command = executor.commands.last()
        val prompt = command.arguments[command.arguments.indexOf("-p") + 1]
        assertTrue(prompt.contains(comment))
        assertTrue(prompt.contains("before-editor-context"))
        assertTrue(prompt.contains("after-editor-context"))
        assertFalse(prompt.contains(projectRoot, ignoreCase = true))
        assertEquals("", command.arguments[command.arguments.indexOf("--tools") + 1])
        assertEquals("none", command.arguments[command.arguments.indexOf("--permission-prompts") + 1])
        assertTrue(command.arguments[command.arguments.indexOf("--disallowedTools") + 1].contains("Read"))
        assertEquals(neutralDirectory, command.workingDirectory)
        assertFalse(command.arguments.any { it.contains(projectRoot, ignoreCase = true) })
    }

    private fun router(codex: RecordingProvider, claude: RecordingProvider): ProviderRouterService = ProviderRouterService(
        ProviderProjectState(),
        mapOf(ProviderId.CODEX to codex, ProviderId.CLAUDE to claude),
        cancelPreview = { preview.cancel() },
        notifySelection = {},
    )

    private class RecordingProvider(
        override val providerId: ProviderId,
        private val result: GenerationResult,
        var session: String? = null,
    ) : LocalGenerationProvider {
        var generateCalls = 0
        val code get() = (result as GenerationResult.Success).code

        override fun checkAvailability(): ProviderDiagnostic = when (result) {
            is GenerationResult.Success -> ProviderDiagnostic.READY
            is GenerationResult.Failure -> ProviderDiagnostic.UNSAFE_CAPABILITIES
        }

        override fun generate(request: GenerationRequest): GenerationResult {
            generateCalls++
            return result
        }

        override fun cancel() = Unit

        override fun isGenerating(): Boolean = false

        override fun resetConversation() { session = null }
    }
}
