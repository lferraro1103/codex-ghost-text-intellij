package com.leandro.codexghosttext.integration

import com.leandro.codexghosttext.actions.CheckCodexConnectionAction
import com.leandro.codexghosttext.actions.GenerateCodexGhostTextAction
import com.leandro.codexghosttext.actions.ResetCodexProjectConversationAction
import com.leandro.codexghosttext.status.ProviderSelectorWidgetFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class PluginCompatibilityTest {
    @Test
    fun `descriptor exposes one persistent selector two providers and stable action registrations`() {
        val descriptor = requireNotNull(javaClass.classLoader.getResource("META-INF/plugin.xml")).readText()

        assertEquals(1, Regex("<statusBarWidgetFactory\\b").findAll(descriptor).count())
        assertTrue(descriptor.contains("id=\"${ProviderSelectorWidgetFactory.WIDGET_ID}\""))
        assertTrue(descriptor.contains("implementation=\"${ProviderSelectorWidgetFactory::class.java.name}\""))
        listOf(
            CheckCodexConnectionAction.ACTION_ID to CheckCodexConnectionAction::class.java.name,
            GenerateCodexGhostTextAction.ACTION_ID to GenerateCodexGhostTextAction::class.java.name,
            "com.leandro.codexghosttext.ResetCodexProjectConversation" to ResetCodexProjectConversationAction::class.java.name,
        ).forEach { (id, implementation) ->
            assertTrue(descriptor.contains("id=\"$id\""))
            assertTrue(descriptor.contains("class=\"$implementation\""))
        }
        assertFalse(descriptor.contains("keyboard-shortcut"))
    }

    @Test
    fun `configured IDE compatibility range covers all planned verifier targets`() {
        val properties = Files.readString(Path.of("gradle.properties"))
        assertTrue(properties.contains("minimumBuild=253"))
        assertTrue(properties.contains("verifierTarget2025_3=2025.3"))
        assertTrue(properties.contains("verifierTarget2026_1=2026.1"))
        assertTrue(properties.contains("verifierTarget2026_2=2026.2"))
    }
}
