package com.leandro.codexghosttext.preview

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints

/**
 * Presentation-only placeholder shown under the selected comment while a provider works.
 *
 * It draws bars, never text, so it cannot be mistaken for a proposal, and like the proposal
 * renderer it holds no reference to the document.
 */
class GhostSkeletonRenderer(
    private val editor: Editor,
    private val indentColumns: Int,
) : EditorCustomElementRenderer {
    /** Advanced by the owning service's timer; the shimmer position is derived from it. */
    @Volatile
    var phase: Float = 0f

    override fun calcWidthInPixels(inlay: Inlay<*>): Int = characterWidth() * (indentColumns + BAR_COLUMNS.max())

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = editor.lineHeight * BAR_COLUMNS.size

    override fun paint(inlay: Inlay<*>, graphics: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        val canvas = graphics as? Graphics2D ?: return
        val oldPaint = canvas.paint
        val oldHint = canvas.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val characterWidth = characterWidth()
        val left = targetRegion.x + indentColumns * characterWidth

        BAR_COLUMNS.forEachIndexed { index, columns ->
            val width = columns * characterWidth
            val top = targetRegion.y + index * editor.lineHeight + VERTICAL_INSET
            val height = (editor.lineHeight - 2 * VERTICAL_INSET).coerceAtLeast(2)
            canvas.paint = BAR
            canvas.fillRoundRect(left, top, width, height, ARC, ARC)
            // A highlight band travels left to right so the block reads as busy, not as content.
            val position = left + (phase % 1f) * (width + HIGHLIGHT_WIDTH) - HIGHLIGHT_WIDTH
            canvas.clip(java.awt.geom.RoundRectangle2D.Float(left.toFloat(), top.toFloat(), width.toFloat(), height.toFloat(), ARC.toFloat(), ARC.toFloat()))
            canvas.paint = java.awt.GradientPaint(position, 0f, TRANSPARENT, position + HIGHLIGHT_WIDTH / 2, 0f, HIGHLIGHT, true)
            canvas.fillRect(left, top, width, height)
            canvas.clip = null
        }

        canvas.paint = oldPaint
        oldHint?.let { canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, it) }
    }

    private fun characterWidth(): Int = editor.contentComponent
        .getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
        .charWidth('m')
        .coerceAtLeast(1)

    private companion object {
        /** Two bars of uneven width, the shape a reader already associates with loading content. */
        val BAR_COLUMNS = listOf(28, 18)
        const val ARC = 6
        const val VERTICAL_INSET = 3
        const val HIGHLIGHT_WIDTH = 90f

        /** Same green family as the accepted-proposal renderer, at a lower, non-committal weight. */
        val BAR = Color(68, 140, 79, 60)
        val HIGHLIGHT = Color(150, 224, 156, 70)
        val TRANSPARENT = Color(150, 224, 156, 0)
    }
}
