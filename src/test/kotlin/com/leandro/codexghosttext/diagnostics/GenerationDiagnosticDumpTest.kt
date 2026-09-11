package com.leandro.codexghosttext.diagnostics

import com.leandro.codexghosttext.generation.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationDiagnosticDumpTest {
    @Test
    fun `dump identifies the selected provider while redacting credential-like failure text`() {
        val dump = GenerationDiagnosticDump.render(
            ProviderId.CLAUDE,
            "No encontré la CLI; token=super-secret-value",
            "generation",
            "C:/work/sample",
        )

        assertTrue(dump.contains("provider=CLAUDE"))
        assertTrue(dump.contains("claudeProbeDiagnostic="))
        assertTrue(dump.contains("claudeSupportedCapabilities="))
        assertTrue(dump.contains("claudeMissingCapabilities="))
        assertTrue(dump.contains("failure=No encontré la CLI; token=<redacted>"))
        assertTrue(dump.contains("projectRoot=C:/work/sample"))
        assertFalse(dump.contains("super-secret-value"))
        assertTrue(dump.contains("redaction="))
    }
}
