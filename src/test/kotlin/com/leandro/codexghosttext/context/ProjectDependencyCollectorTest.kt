package com.leandro.codexghosttext.context

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ProjectDependencyCollectorTest : LightJavaCodeInsightFixtureTestCase() {
    fun testNamesTheProjectFileTheSurroundingCodeResolvesTo() {
        myFixture.addFileToProject("Nodo.java", "public class Nodo { public Nodo(int valor) {} public void sacarRaiz() {} }")
        val source = """
            public class Arbol {
                void construir() {
                    // crear un nodo raíz
                    Nodo nodo = new Nodo(1);
                }
            }
        """.trimIndent()
        myFixture.configureByText("Arbol.java", source)
        val comment = "// crear un nodo raíz"
        val start = source.indexOf(comment)

        val dependencies = collect(TextRange(start, start + comment.length))

        assertTrue(dependencies.paths.any { it.endsWith("Nodo.java") })
        assertFalse(dependencies.paths.any { it.endsWith("Arbol.java") })
    }

    fun testSkeletonCarriesDeclarationsWithoutBodies() {
        myFixture.addFileToProject(
            "Nodo.java",
            """
            public class Nodo {
                private final int valor;
                public Nodo(int valor) { this.valor = valor; }
                public int getValor() { return valor + 41; }
            }
            """.trimIndent(),
        )
        val source = """
            public class Arbol {
                void construir() {
                    // crear un nodo raíz
                    Nodo nodo = new Nodo(1);
                }
            }
        """.trimIndent()
        myFixture.configureByText("Arbol.java", source)
        val comment = "// crear un nodo raíz"
        val start = source.indexOf(comment)

        val skeleton = collect(TextRange(start, start + comment.length)).skeletons.firstOrNull().orEmpty()

        assertTrue(skeleton.contains("Nodo"))
        assertTrue(skeleton.contains("getValor"))
        // A skeleton is signatures only: no statement from any method body may appear.
        assertFalse(skeleton.contains("valor + 41"))
        assertFalse(skeleton.contains("this.valor ="))
    }

    fun testResolvesNothingWhenTheCommentStandsAlone() {
        myFixture.configureByText("Solo.java", "// sin referencias\n")

        val dependencies = collect(TextRange(0, "// sin referencias".length))

        assertTrue(dependencies.isEmpty)
    }

    fun testStopsAtTheFileBudget() {
        repeat(ProjectDependencyCollector.MAX_FILES + 4) { index ->
            myFixture.addFileToProject("Tipo$index.java", "public class Tipo$index { public Tipo$index() {} }")
        }
        val uses = (0 until ProjectDependencyCollector.MAX_FILES + 4).joinToString("\n        ") { "Tipo$it t$it = new Tipo$it();" }
        val source = """
            public class Muchos {
                void construir() {
                    // crear todos
                    $uses
                }
            }
        """.trimIndent()
        myFixture.configureByText("Muchos.java", source)
        val comment = "// crear todos"
        val start = source.indexOf(comment)

        val dependencies = collect(TextRange(start, start + comment.length))

        assertTrue(dependencies.paths.size <= ProjectDependencyCollector.MAX_FILES)
    }

    private fun collect(range: TextRange): ProjectDependencyCollector.Dependencies =
        ReadAction.compute<ProjectDependencyCollector.Dependencies, RuntimeException> {
            ProjectDependencyCollector.collect(project, myFixture.file, range, withSkeletons = true)
        }
}
