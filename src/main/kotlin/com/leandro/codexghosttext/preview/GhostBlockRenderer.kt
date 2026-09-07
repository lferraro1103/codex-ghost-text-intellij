package com.leandro.codexghosttext.preview

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.colors.EditorFontType
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

/** Presentation-only renderer. It deliberately has no reference to the document. */
class GhostBlockRenderer(
    private val editor: Editor,
    private val text: String,
) : EditorCustomElementRenderer {
    private val lines = text.lines()

    override fun calcWidthInPixels(inlay: com.intellij.openapi.editor.Inlay<*>): Int {
        val metrics = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
        return lines.maxOfOrNull { metrics.stringWidth(it) } ?: 0
    }

    override fun calcHeightInPixels(inlay: com.intellij.openapi.editor.Inlay<*>): Int =
        editor.lineHeight * lines.size

    override fun paint(inlay: com.intellij.openapi.editor.Inlay<*>, graphics: Graphics, targetRegion: Rectangle, textAttributes: com.intellij.openapi.editor.markup.TextAttributes) {
        val oldColor = graphics.color
        val metrics = graphics.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
        graphics.font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        lines.forEachIndexed { index, line ->
            val lineY = targetRegion.y + index * editor.lineHeight
            val lineWidth = metrics.stringWidth(line)
            graphics.color = INSERT_BACKGROUND
            graphics.fillRect(targetRegion.x, lineY, lineWidth, editor.lineHeight)
            graphics.color = INSERT_FOREGROUND
            graphics.drawString(line, targetRegion.x, lineY + metrics.ascent)
        }
        graphics.color = oldColor
    }

    private companion object {
        /** Green insertion treatment; no red region exists because this plugin never deletes document text. */
        val INSERT_BACKGROUND = Color(68, 140, 79, 78)
        val INSERT_FOREGROUND = Color(150, 224, 156, 205)
    }
}
