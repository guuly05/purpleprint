package com.example.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

object MarkdownInlineParser {
    fun parse(text: String, accentColor: Color): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                when {
                    text.startsWith("**", i) -> {
                        val endIdx = text.indexOf("**", i + 2)
                        if (endIdx != -1) {
                            val inner = text.substring(i + 2, endIdx)
                            val start = length
                            append(inner)
                            addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                            i = endIdx + 2
                        } else {
                            append("**")
                            i += 2
                        }
                    }
                    text.startsWith("__", i) -> {
                        val endIdx = text.indexOf("__", i + 2)
                        if (endIdx != -1) {
                            val inner = text.substring(i + 2, endIdx)
                            val start = length
                            append(inner)
                            addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                            i = endIdx + 2
                        } else {
                            append("__")
                            i += 2
                        }
                    }
                    text.startsWith("*", i) -> {
                        val endIdx = text.indexOf("*", i + 1)
                        if (endIdx != -1) {
                            val inner = text.substring(i + 1, endIdx)
                            val start = length
                            append(inner)
                            addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                            i = endIdx + 1
                        } else {
                            append("*")
                            i += 1
                        }
                    }
                    text.startsWith("_", i) -> {
                        val endIdx = text.indexOf("_", i + 1)
                        if (endIdx != -1) {
                            val inner = text.substring(i + 1, endIdx)
                            val start = length
                            append(inner)
                            addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                            i = endIdx + 1
                        } else {
                            append("_")
                            i += 1
                        }
                    }
                    text.startsWith("`", i) -> {
                        val endIdx = text.indexOf("`", i + 1)
                        if (endIdx != -1) {
                            val inner = text.substring(i + 1, endIdx)
                            val start = length
                            append(inner)
                            addStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    color = accentColor,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                start,
                                length
                            )
                            i = endIdx + 1
                        } else {
                            append("`")
                            i += 1
                        }
                    }
                    else -> {
                        append(text[i])
                        i++
                    }
                }
            }
        }
    }
}
