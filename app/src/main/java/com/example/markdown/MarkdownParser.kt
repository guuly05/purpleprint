package com.example.markdown

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val content: String, val language: String = "") : MarkdownBlock()
    data class ListItem(val ordered: Boolean, val index: Int, val text: String) : MarkdownBlock()
    data class BlockQuote(val text: String) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
}

object MarkdownParser {
    fun parse(text: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = text.split("\n")
        var inCodeBlock = false
        val currentCodeBlock = StringBuilder()
        var codeBlockLang = ""

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (inCodeBlock) {
                if (trimmed.startsWith("```")) {
                    inCodeBlock = false
                    blocks.add(MarkdownBlock.CodeBlock(currentCodeBlock.toString().trimEnd(), codeBlockLang))
                    currentCodeBlock.clear()
                } else {
                    currentCodeBlock.append(line).append("\n")
                }
                i++
                continue
            }

            if (trimmed.startsWith("```")) {
                inCodeBlock = true
                codeBlockLang = trimmed.removePrefix("```").trim()
                i++
                continue
            }

            when {
                trimmed.startsWith("---") || trimmed.startsWith("***") -> {
                    blocks.add(MarkdownBlock.HorizontalRule)
                }
                trimmed.startsWith("#") -> {
                    var hashes = 0
                    while (hashes < trimmed.length && trimmed[hashes] == '#') {
                        hashes++
                    }
                    if (hashes in 1..6 && hashes < trimmed.length && trimmed[hashes] == ' ') {
                        val headingText = trimmed.substring(hashes + 1).trim()
                        blocks.add(MarkdownBlock.Heading(hashes, headingText))
                    } else {
                        blocks.add(MarkdownBlock.Paragraph(line))
                    }
                }
                trimmed.startsWith(">") -> {
                    val quoteText = trimmed.removePrefix(">").trim()
                    blocks.add(MarkdownBlock.BlockQuote(quoteText))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val itemText = trimmed.substring(2).trim()
                    blocks.add(MarkdownBlock.ListItem(ordered = false, index = 0, text = itemText))
                }
                trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val dotIdx = trimmed.indexOf('.')
                    val numStr = trimmed.substring(0, dotIdx)
                    val itemText = trimmed.substring(dotIdx + 1).trim()
                    val idx = numStr.toIntOrNull() ?: 1
                    blocks.add(MarkdownBlock.ListItem(ordered = true, index = idx, text = itemText))
                }
                trimmed.isEmpty() -> {
                    // Keep spacing or empty line if inside text area, but parser skips empty blocks
                }
                else -> {
                    blocks.add(MarkdownBlock.Paragraph(line))
                }
            }
            i++
        }

        if (inCodeBlock && currentCodeBlock.isNotEmpty()) {
            blocks.add(MarkdownBlock.CodeBlock(currentCodeBlock.toString().trimEnd(), codeBlockLang))
        }

        return blocks
    }
}
