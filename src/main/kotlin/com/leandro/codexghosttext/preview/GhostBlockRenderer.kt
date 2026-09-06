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
        val foreground = editor.colorsScheme.defaultForeground
        graphics.color = Color(foreground.red, foreground.green, foreground.blue, 150)
        val metrics = graphics.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
        graphics.font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        lines.forEachIndexed { index, line ->
            graphics.drawString(line, targetRegion.x, targetRegion.y + metrics.ascent + index * editor.lineHeight)
        }
        graphics.color = oldColor
    }
}
