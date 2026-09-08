package com.leandro.codexghosttext.status

import com.leandro.codexghosttext.generation.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationStatusServiceTest {
    @Test
    fun `spinner text captures the provider that started the request`() {
        assertEquals("◐ Codex: generando…", GenerationStatusPresentation.text(ProviderId.CODEX, 0))
        assertEquals("◐ Claude: generando…", GenerationStatusPresentation.text(ProviderId.CLAUDE, 0))
    }

    @Test
    fun `old request token cannot finish a newer spinner`() {
        val tracker = GenerationRequestTracker()
        val oldRequest = tracker.begin()
        val activeRequest = tracker.begin()

        assertFalse(tracker.finish(oldRequest))
        assertEquals(activeRequest, tracker.activeRequest())
        assertTrue(tracker.finish(activeRequest))
        assertEquals(0L, tracker.activeRequest())
    }
}
