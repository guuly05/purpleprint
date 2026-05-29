package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.text.TextPaint
import com.example.markdown.MarkdownBlock
import com.example.markdown.MarkdownParser
import java.io.FileOutputStream
import java.io.IOException

// Helper data classes for styled inline text rendering
data class AttributedChar(
    val char: Char,
    val bold: Boolean,
    val italic: Boolean,
    val code: Boolean
)

data class StyledChunk(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false
)

interface RenderRow {
    val height: Float
    fun draw(canvas: Canvas, xMin: Float, xMax: Float, y: Float, paints: PdfPaints)
}

// Global paints container for consistent rendering styles on PDF printing
class PdfPaints {
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 10f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 10f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val italicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 10f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
    }
    val boldItalicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 10f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
    }
    val monoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 9f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    val headerPaints = (1..6).map { level ->
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = when (level) {
                1 -> 20f
                2 -> 16f
                3 -> 14f
                4 -> 12f
                else -> 10f
            }
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    val bgCodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F1F5F9") // light slate grey block background
        style = Paint.Style.FILL
    }
    val borderCodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
    }
    val borderQuotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        style = Paint.Style.FILL
    }
    val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
}

// RenderRow Implementations
class SpacingRow(override val height: Float) : RenderRow {
    override fun draw(canvas: Canvas, xMin: Float, xMax: Float, y: Float, paints: PdfPaints) {}
}

class HeadingRow(val text: String, val level: Int) : RenderRow {
    private val sizeMultiplier = when (level) {
        1 -> 20f
        2 -> 16f
        3 -> 14f
        4 -> 12f
        else -> 10f
    }
    override val height: Float = sizeMultiplier * 1.5f

    override fun draw(canvas: Canvas, xMin: Float, xMax: Float, y: Float, paints: PdfPaints) {
        val paint = paints.headerPaints.getOrElse(level - 1) { paints.headerPaints.last() }
        canvas.drawText(text, xMin, y + sizeMultiplier * 1.2f, paint)
    }
}

class ParagraphRow(val chunks: List<StyledChunk>) : RenderRow {
    override val height: Float = 10f * 1.5f

    override fun draw(canvas: Canvas, xMin: Float, xMax: Float, y: Float, paints: PdfPaints) {
        var currentX = xMin
        val baseline = y + 10f * 1.25f
        for (chunk in chunks) {
            val p = when {
                chunk.code -> paints.monoPaint
                chunk.bold && chunk.italic -> paints.boldItalicPaint
                chunk.bold -> paints.boldPaint
                chunk.italic -> paints.italicPaint
                else -> paints.textPaint
            }
            canvas.drawText(chunk.text, currentX, baseline, p)
            currentX += p.measureText(chunk.text)
        }
    }
}

class BlockQuoteRow(val chunks: List<StyledChunk>) : RenderRow {
    override val height: Float = 10f * 1.5f

    override fun draw(canvas: Canvas, xMin: Float, xMax: Float, y: Float, paints: PdfPaints) {
        val barWidth = 3f
        val barSpacing = 8f
        canvas.drawRect(xMin, y, xMin + barWidth, y + height, paints.borderQuotePaint)

        var currentX = xMin + barWidth + barSpacing
        val baseline = y + 10f * 1.25f
        for (chunk in chunks) {
            val p = when {
                chunk.code -> paints.monoPaint
                chunk.bold && chunk.italic -> paints.boldItalicPaint
                chunk.bold -> paints.boldPaint
                chunk.italic -> paints.italicPaint
                else -> paints.textPaint
            }
            canvas.drawText(chunk.text, currentX, baseline, p)
            currentX += p.measureText(chunk.text)
        }
    }
}

class ListItemRow(val bulletText: String, val chunks: List<StyledChunk>, val leadWidth: Float) : RenderRow {
    override val height: Float = 10f * 1.5f

    override fun draw(canvas: Canvas, xMin: Float, xMax: Float, y: Float, paints: PdfPaints) {
        val baseline = y + 10f * 1.25f
        if (bulletText.isNotEmpty()) {
            canvas.drawText(bulletText, xMin + 4f, baseline, paints.boldPaint)
        }
        var currentX = xMin + leadWidth
        for (chunk in chunks) {
            val p = when {
                chunk.code -> paints.monoPaint
                chunk.bold && chunk.italic -> paints.boldItalicPaint
                chunk.bold -> paints.boldPaint
                chunk.italic -> paints.italicPaint
                else -> paints.textPaint
            }
            canvas.drawText(chunk.text, currentX, baseline, p)
            currentX += p.measureText(chunk.text)
        }
    }
}

class CodeBlockLineRow(
    val line: String,
    val isFirst: Boolean,
    val isLast: Boolean,
    val padding: Float
) : RenderRow {
    override val height: Float = 9f * 1.6f

    override fun draw(canvas: Canvas, xMin: Float, xMax: Float, y: Float, paints: PdfPaints) {
        val topPad = if (isFirst) 4f else 0f
        val bottomPad = if (isLast) 4f else 0f
        
        val rect = RectF(xMin, y - topPad, xMax, y + height + bottomPad)
        canvas.drawRect(rect, paints.bgCodePaint)
        
        canvas.drawLine(xMin, y - topPad, xMin, y + height + bottomPad, paints.borderCodePaint)
        canvas.drawLine(xMax, y - topPad, xMax, y + height + bottomPad, paints.borderCodePaint)
        
        if (isFirst) {
            canvas.drawLine(xMin, y - topPad, xMax, y - topPad, paints.borderCodePaint)
        }
        if (isLast) {
            canvas.drawLine(xMin, y + height + bottomPad, xMax, y + height + bottomPad, paints.borderCodePaint)
        }

        val baseline = y + 9f * 1.25f
        canvas.drawText(line, xMin + padding, baseline, paints.monoPaint)
    }
}

class HorizontalRuleRow : RenderRow {
    override val height: Float = 4f
    override fun draw(canvas: Canvas, xMin: Float, xMax: Float, y: Float, paints: PdfPaints) {
        val centerY = y + height / 2f
        canvas.drawLine(xMin, centerY, xMax, centerY, paints.rulePaint)
    }
}

// Custom PrintDocumentAdapter implementation
class MarkdownPrintAdapter(
    private val context: Context,
    private val markdownText: String,
    private val documentTitle: String = "PurplePrintDocument"
) : PrintDocumentAdapter() {

    private var mPageWidth = 595 // default A4 Points width (72 points/inch)
    private var mPageHeight = 842 // default A4 Points height
    private val mMargin = 50f
    private val mPages = mutableListOf<List<RenderRow>>()

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        // Detect user paper size attributes and map standard media types to PostScript points
        val mediaSize = newAttributes.mediaSize
        if (mediaSize != null) {
            when (mediaSize) {
                PrintAttributes.MediaSize.ISO_A4 -> {
                    mPageWidth = 595
                    mPageHeight = 842
                }
                PrintAttributes.MediaSize.NA_LETTER -> {
                    mPageWidth = 612
                    mPageHeight = 792
                }
                PrintAttributes.MediaSize.NA_LEGAL -> {
                    mPageWidth = 612
                    mPageHeight = 1008
                }
                PrintAttributes.MediaSize.ISO_A3 -> {
                    mPageWidth = 842
                    mPageHeight = 1191
                }
                else -> {
                    mPageWidth = 595
                    mPageHeight = 842
                }
            }
        }

        // Precalculate pages layout
        val paints = PdfPaints()
        computePages(paints)

        val pageCount = mPages.size.coerceAtLeast(1)

        val info = PrintDocumentInfo.Builder("$documentTitle.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount)
            .build()

        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }

        val pdfDocument = PdfDocument()
        val paints = PdfPaints()

        try {
            for (i in 0 until mPages.size) {
                if (shouldRenderPage(i, pages)) {
                    val pageInfo = PdfDocument.PageInfo.Builder(mPageWidth, mPageHeight, i).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // Canvas background white
                    canvas.drawColor(Color.WHITE)

                    val xMin = mMargin
                    val xMax = mPageWidth - mMargin
                    var currentY = mMargin

                    val pageRows = mPages[i]
                    for (row in pageRows) {
                        row.draw(canvas, xMin, xMax, currentY, paints)
                        currentY += row.height
                    }

                    // Bottom page numbering
                    val pageNumText = "Page ${i + 1} of ${mPages.size}"
                    val pageNumPaint = paints.italicPaint.apply {
                        color = Color.GRAY
                        textSize = 8f
                    }
                    val textWidth = pageNumPaint.measureText(pageNumText)
                    canvas.drawText(
                        pageNumText,
                        (mPageWidth - textWidth) / 2f,
                        mPageHeight - mMargin / 2f,
                        pageNumPaint
                    )

                    pdfDocument.finishPage(page)
                }
            }

            FileOutputStream(destination.fileDescriptor).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            callback.onWriteFinished(pages)

        } catch (e: IOException) {
            callback.onWriteFailed(e.toString())
        } finally {
            pdfDocument.close()
        }
    }

    private fun shouldRenderPage(pageIdx: Int, ranges: Array<out PageRange>): Boolean {
        for (range in ranges) {
            if (pageIdx >= range.start && pageIdx <= range.end) {
                return true
            }
        }
        return false
    }

    private fun computePages(paints: PdfPaints) {
        val usableWidth = mPageWidth - 2 * mMargin
        val usableHeight = mPageHeight - 2 * mMargin

        val blocks = MarkdownParser.parse(markdownText)
        val allRows = buildRenderRows(blocks, paints, usableWidth)

        mPages.clear()
        var currentPage = mutableListOf<RenderRow>()
        var currentPageUsedHeight = 0f

        for (row in allRows) {
            val rowHeight = row.height
            if (currentPageUsedHeight + rowHeight > usableHeight) {
                if (currentPage.isNotEmpty()) {
                    mPages.add(currentPage)
                    currentPage = mutableListOf()
                    currentPageUsedHeight = 0f
                }
            }
            currentPage.add(row)
            currentPageUsedHeight += rowHeight
        }
        if (currentPage.isNotEmpty()) {
            mPages.add(currentPage)
        }
    }

    private fun buildRenderRows(
        blocks: List<MarkdownBlock>,
        paints: PdfPaints,
        maxTextWidth: Float
    ): List<RenderRow> {
        val rows = mutableListOf<RenderRow>()
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Heading -> {
                    rows.add(SpacingRow(12f))
                    val headingP = paints.headerPaints.getOrElse(block.level - 1) { paints.headerPaints.last() }
                    val headingText = block.text

                    val words = headingText.split(" ")
                    val headingLines = mutableListOf<String>()
                    var currentLine = StringBuilder()
                    for (word in words) {
                        val test = if (currentLine.isEmpty()) word else "$currentLine $word"
                        if (headingP.measureText(test) <= maxTextWidth) {
                            currentLine.append(if (currentLine.isEmpty()) "" else " ").append(word)
                        } else {
                            if (currentLine.isNotEmpty()) {
                                headingLines.add(currentLine.toString())
                                currentLine = StringBuilder(word)
                            } else {
                                headingLines.add(word)
                            }
                        }
                    }
                    if (currentLine.isNotEmpty()) {
                        headingLines.add(currentLine.toString())
                    }

                    for (line in headingLines) {
                        rows.add(HeadingRow(line, block.level))
                    }
                    rows.add(SpacingRow(6f))
                }
                is MarkdownBlock.Paragraph -> {
                    rows.add(SpacingRow(6f))
                    val chars = parseToAttributedChars(block.text)
                    val lines = wrapAttributedChars(chars, paints, maxTextWidth)
                    for (line in lines) {
                        val chunks = groupToStyledChunks(line)
                        rows.add(ParagraphRow(chunks))
                    }
                    rows.add(SpacingRow(4f))
                }
                is MarkdownBlock.BlockQuote -> {
                    rows.add(SpacingRow(6f))
                    val quoteWidth = maxTextWidth - 12f
                    val chars = parseToAttributedChars(block.text)
                    val lines = wrapAttributedChars(chars, paints, quoteWidth)
                    for (line in lines) {
                        val chunks = groupToStyledChunks(line)
                        rows.add(BlockQuoteRow(chunks))
                    }
                    rows.add(SpacingRow(6f))
                }
                is MarkdownBlock.ListItem -> {
                    rows.add(SpacingRow(4f))
                    val leadWidth = 20f
                    val listWidth = maxTextWidth - leadWidth
                    val chars = parseToAttributedChars(block.text)
                    val lines = wrapAttributedChars(chars, paints, listWidth)

                    for (idx in lines.indices) {
                        val line = lines[idx]
                        val chunks = groupToStyledChunks(line)
                        val bulletText = if (idx == 0) {
                            if (block.ordered) "${block.index}." else "*"
                        } else {
                            ""
                        }
                        rows.add(ListItemRow(bulletText, chunks, leadWidth))
                    }
                    rows.add(SpacingRow(4f))
                }
                is MarkdownBlock.CodeBlock -> {
                    rows.add(SpacingRow(8f))
                    val padding = 6f
                    val innerWidth = maxTextWidth - (padding * 2)

                    val codeLines = block.content.split("\n")
                    val wrappedCodeLines = mutableListOf<String>()
                    for (rawCodeLine in codeLines) {
                        if (rawCodeLine.isEmpty()) {
                            wrappedCodeLines.add("")
                            continue
                        }
                        val words = rawCodeLine.split(" ")
                        var currentLine = StringBuilder()
                        for (word in words) {
                            val test = if (currentLine.isEmpty()) word else "$currentLine $word"
                            if (paints.monoPaint.measureText(test) <= innerWidth) {
                                currentLine.append(if (currentLine.isEmpty()) "" else " ").append(word)
                            } else {
                                if (currentLine.isNotEmpty()) {
                                    wrappedCodeLines.add(currentLine.toString())
                                    currentLine = StringBuilder(word)
                                } else {
                                    wrappedCodeLines.add(word)
                                }
                            }
                        }
                        if (currentLine.isNotEmpty()) {
                            wrappedCodeLines.add(currentLine.toString())
                        }
                    }

                    for (idx in wrappedCodeLines.indices) {
                        val line = wrappedCodeLines[idx]
                        val isFirst = idx == 0
                        val isLast = idx == wrappedCodeLines.size - 1
                        rows.add(CodeBlockLineRow(line, isFirst, isLast, padding))
                    }
                    rows.add(SpacingRow(8f))
                }
                is MarkdownBlock.HorizontalRule -> {
                    rows.add(SpacingRow(10f))
                    rows.add(HorizontalRuleRow())
                    rows.add(SpacingRow(10f))
                }
            }
        }
        return rows
    }

    private fun parseToAttributedChars(text: String): List<AttributedChar> {
        val list = mutableListOf<AttributedChar>()
        val pattern = Regex("(\\*\\*.*?\\*\\*|\\*.*?\\*|`.*?`|[^*`]+)")
        val matches = pattern.findAll(text)
        for (match in matches) {
            val raw = match.value
            when {
                raw.startsWith("**") && raw.endsWith("**") && raw.length >= 4 -> {
                    val clean = raw.substring(2, raw.length - 2)
                    for (c in clean) list.add(AttributedChar(c, bold = true, italic = false, code = false))
                }
                raw.startsWith("*") && raw.endsWith("*") && raw.length >= 2 -> {
                    val clean = raw.substring(1, raw.length - 1)
                    for (c in clean) list.add(AttributedChar(c, bold = false, italic = true, code = false))
                }
                raw.startsWith("`") && raw.endsWith("`") && raw.length >= 2 -> {
                    val clean = raw.substring(1, raw.length - 1)
                    for (c in clean) list.add(AttributedChar(c, bold = false, italic = false, code = true))
                }
                else -> {
                    for (c in raw) list.add(AttributedChar(c, bold = false, italic = false, code = false))
                }
            }
        }
        return list
    }

    private fun wrapAttributedChars(
        chars: List<AttributedChar>,
        paints: PdfPaints,
        maxWidth: Float
    ): List<List<AttributedChar>> {
        val words = mutableListOf<List<AttributedChar>>()
        var currentWord = mutableListOf<AttributedChar>()

        for (char in chars) {
            if (char.char == ' ') {
                if (currentWord.isNotEmpty()) {
                    words.add(currentWord)
                    currentWord = mutableListOf()
                }
                words.add(listOf(char))
            } else {
                currentWord.add(char)
            }
        }
        if (currentWord.isNotEmpty()) {
            words.add(currentWord)
        }

        val lines = mutableListOf<List<AttributedChar>>()
        var currentLine = mutableListOf<AttributedChar>()
        var currentLineWidth = 0f

        for (word in words) {
            val wordWidth = word.sumOf { c ->
                val p = when {
                    c.code -> paints.monoPaint
                    c.bold && c.italic -> paints.boldItalicPaint
                    c.bold -> paints.boldPaint
                    c.italic -> paints.italicPaint
                    else -> paints.textPaint
                }
                p.measureText(c.char.toString()).toDouble()
            }.toFloat()

            if (currentLine.isEmpty()) {
                if (word.size == 1 && word[0].char == ' ') {
                    continue
                }
                currentLine.addAll(word)
                currentLineWidth = wordWidth
            } else {
                if (currentLineWidth + wordWidth <= maxWidth) {
                    currentLine.addAll(word)
                    currentLineWidth += wordWidth
                } else {
                    if (word.size == 1 && word[0].char == ' ') {
                        lines.add(currentLine)
                        currentLine = mutableListOf()
                        currentLineWidth = 0f
                    } else {
                        lines.add(currentLine)
                        currentLine = mutableListOf()
                        currentLine.addAll(word)
                        currentLineWidth = wordWidth
                    }
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            if (currentLine.size > 1 && currentLine.last().char == ' ') {
                currentLine.removeAt(currentLine.size - 1)
            }
            lines.add(currentLine)
        }
        return lines
    }

    private fun groupToStyledChunks(line: List<AttributedChar>): List<StyledChunk> {
        if (line.isEmpty()) return emptyList()
        val chunks = mutableListOf<StyledChunk>()
        var currentBold = line[0].bold
        var currentItalic = line[0].italic
        var currentCode = line[0].code
        val sb = StringBuilder().append(line[0].char)

        for (i in 1 until line.size) {
            val c = line[i]
            if (c.bold == currentBold && c.italic == currentItalic && c.code == currentCode) {
                sb.append(c.char)
            } else {
                chunks.add(StyledChunk(sb.toString(), currentBold, currentItalic, currentCode))
                currentBold = c.bold
                currentItalic = c.italic
                currentCode = c.code
                sb.clear().append(c.char)
            }
        }
        chunks.add(StyledChunk(sb.toString(), currentBold, currentItalic, currentCode))
        return chunks
    }
}
