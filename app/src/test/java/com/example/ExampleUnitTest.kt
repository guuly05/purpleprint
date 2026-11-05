package com.example

import com.example.markdown.MarkdownBlock
import com.example.markdown.MarkdownParser
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun markdownParser_recognizesSupportedBlocks() {
    val blocks = MarkdownParser.parse(
      """
      # Title
      Paragraph with **bold** text.
      - unordered item
      1. ordered item
      > quote
      ---
      ```kotlin
      val answer = 42
      ```
      """.trimIndent()
    )

    assertEquals(MarkdownBlock.Heading(1, "Title"), blocks[0])
    assertEquals(MarkdownBlock.Paragraph("Paragraph with **bold** text."), blocks[1])
    assertEquals(MarkdownBlock.ListItem(ordered = false, index = 0, text = "unordered item"), blocks[2])
    assertEquals(MarkdownBlock.ListItem(ordered = true, index = 1, text = "ordered item"), blocks[3])
    assertEquals(MarkdownBlock.BlockQuote("quote"), blocks[4])
    assertEquals(MarkdownBlock.HorizontalRule, blocks[5])
    assertEquals(MarkdownBlock.CodeBlock("val answer = 42", "kotlin"), blocks[6])
  }

  @Test
  fun markdownParser_joinsParagraphsAndRecognizesTasksAndRules() {
    val blocks = MarkdownParser.parse("First line\ncontinued line\n\n+ [x] done\n- [ ] pending\n\n___")
    assertEquals(MarkdownBlock.Paragraph("First line continued line"), blocks[0])
    assertEquals(MarkdownBlock.ListItem(false, 0, "done", true), blocks[1])
    assertEquals(MarkdownBlock.ListItem(false, 0, "pending", false), blocks[2])
    assertEquals(MarkdownBlock.HorizontalRule, blocks[3])
  }

  @Test
  fun markdownParser_keepsUnclosedCodeFenceContent() {
    assertEquals(
      MarkdownBlock.CodeBlock("line one\nline two", "kotlin"),
      MarkdownParser.parse("```kotlin\nline one\nline two").single()
    )
  }
}
