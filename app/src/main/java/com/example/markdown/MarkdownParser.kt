package com.example.markdown

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val content: String, val language: String = "") : MarkdownBlock()
    data class ListItem(
        val ordered: Boolean,
        val index: Int,
        val text: String,
        val checked: Boolean? = null
    ) : MarkdownBlock()
    data class BlockQuote(val text: String) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
}

/** A small, forgiving block parser for the Markdown commonly used in notes. */
object MarkdownParser {
    private val orderedItem = Regex("^(\\d+)[.)]\\s+(.+)$")
    private val unorderedItem = Regex("^[-+*]\\s+(.+)$")
    private val taskItem = Regex("^[-+*]\\s+\\[([ xX])]\\s*(.*)$")
    private val rule = Regex("^(?:(?:\\*\\s*){3,}|(?:-\\s*){3,}|(?:_\\s*){3,})$")

    fun parse(text: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val paragraph = mutableListOf<String>()
        val code = mutableListOf<String>()
        var codeLanguage = ""
        var inFence = false

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" ").trim())
                paragraph.clear()
            }
        }
        fun flushCode() {
            blocks += MarkdownBlock.CodeBlock(code.joinToString("\n").trimEnd(), codeLanguage)
            code.clear()
            codeLanguage = ""
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (inFence) {
                if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                    flushCode()
                    inFence = false
                } else code += line
                continue
            }
            val fence = trimmed.startsWith("```") || trimmed.startsWith("~~~")
            if (fence) {
                flushParagraph()
                inFence = true
                codeLanguage = trimmed.drop(3).trim()
                continue
            }
            if (trimmed.isEmpty()) {
                flushParagraph()
                continue
            }

            val heading = Regex("^(#{1,6})\\s+(.+?)\\s*#*\\s*$").matchEntire(trimmed)
            val task = taskItem.matchEntire(trimmed)
            val ordered = orderedItem.matchEntire(trimmed)
            val unordered = unorderedItem.matchEntire(trimmed)
            when {
                heading != null -> {
                    flushParagraph()
                    blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
                }
                rule.matches(trimmed) -> {
                    flushParagraph()
                    blocks += MarkdownBlock.HorizontalRule
                }
                trimmed.startsWith(">") -> {
                    flushParagraph()
                    blocks += MarkdownBlock.BlockQuote(trimmed.removePrefix(">").trim())
                }
                task != null -> {
                    flushParagraph()
                    blocks += MarkdownBlock.ListItem(false, 0, task.groupValues[2], task.groupValues[1].equals("x", true))
                }
                ordered != null -> {
                    flushParagraph()
                    blocks += MarkdownBlock.ListItem(true, ordered.groupValues[1].toIntOrNull() ?: 1, ordered.groupValues[2])
                }
                unordered != null -> {
                    flushParagraph()
                    blocks += MarkdownBlock.ListItem(false, 0, unordered.groupValues[1])
                }
                else -> paragraph += trimmed
            }
        }
        if (inFence) flushCode()
        flushParagraph()
        return blocks
    }
}
