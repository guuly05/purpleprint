package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.markdown.MarkdownBlock
import com.example.markdown.MarkdownParser
import com.example.pdf.MarkdownPrintAdapter
import com.example.ui.theme.PurplePrintTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            
            PurplePrintTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PurplePrintApp(
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = { isDarkTheme = !isDarkTheme }
                    )
                }
            }
        }
    }
}

private const val DEFAULT_MARKDOWN = """# Project PurplePrint

## Specifications
This application converts **Markdown** into high-quality PDF documents using native Android drawing APIs.

- Performance optimized
- Zero external dependencies
- Material 3 Obsidian Theme

```kotlin
val printer = PurplePrintEngine()
printer.export(markdownContent)
```

> Rendered on *A4 Canvas*"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurplePrintApp(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("purpleprint", Context.MODE_PRIVATE) }
    var markdownText by rememberSaveable { mutableStateOf(preferences.getString("draft", null) ?: DEFAULT_MARKDOWN) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showFindDialog by remember { mutableStateOf(false) }
    var showHeadingsDialog by remember { mutableStateOf(false) }
    var findText by rememberSaveable { mutableStateOf("") }
    var findSelection by remember { mutableStateOf<Int?>(null) }
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var activeTab by rememberSaveable { mutableStateOf(0) } // 0 = Editor, 1 = Preview

    LaunchedEffect(markdownText) { preferences.edit().putString("draft", markdownText).apply() }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // SAF File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val importedText = reader.readText()
                        markdownText = importedText
                        if (importedText.isBlank()) Toast.makeText(context, "The selected file is empty", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(context, "Markdown opened", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error importing file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val saveMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(markdownText) }
            }
            Toast.makeText(context, "Markdown saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not save file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // PDF Printer Launcher
    val triggerPrint = {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val title = MarkdownParser.parse(markdownText).filterIsInstance<MarkdownBlock.Heading>().firstOrNull()?.text
                ?.take(60)?.replace(Regex("[^A-Za-z0-9 _-]"), "")?.ifBlank { null } ?: "PurplePrint_Document"
            val adapter = MarkdownPrintAdapter(context, markdownText, title.replace(' ', '_'))
            printManager.print(title, adapter, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to initialize print: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Logo box gradient
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    SolidColor(MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "P",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Text(
                            text = "PurplePrint",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { actionMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Document actions")
                    }
                    DropdownMenu(expanded = actionMenuExpanded, onDismissRequest = { actionMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Open Markdown") }, leadingIcon = { Icon(Icons.Default.UploadFile, null) }, onClick = {
                            actionMenuExpanded = false
                            filePickerLauncher.launch(arrayOf("text/plain", "text/markdown", "text/x-markdown", "*/*"))
                        }, modifier = Modifier.testTag("import_button"))
                        DropdownMenuItem(text = { Text("Save Markdown") }, leadingIcon = { Icon(Icons.Default.Save, null) }, onClick = {
                            actionMenuExpanded = false
                            saveMarkdownLauncher.launch("${documentTitle(markdownText)}.md")
                        })
                        DropdownMenuItem(text = { Text("Share Markdown") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = {
                            actionMenuExpanded = false
                            val send = Intent(Intent.ACTION_SEND).setType("text/markdown")
                                .putExtra(Intent.EXTRA_SUBJECT, documentTitle(markdownText))
                                .putExtra(Intent.EXTRA_TEXT, markdownText)
                            context.startActivity(Intent.createChooser(send, "Share Markdown"))
                        })
                        DropdownMenuItem(text = { Text(if (isDarkTheme) "Light theme" else "Dark theme") }, leadingIcon = { Icon(if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, null) }, onClick = {
                            actionMenuExpanded = false
                            onThemeToggle()
                        }, modifier = Modifier.testTag("theme_toggle"))
                        DropdownMenuItem(text = { Text("Find in document") }, leadingIcon = { Icon(Icons.Default.Search, null) }, onClick = {
                            actionMenuExpanded = false
                            showFindDialog = true
                        })
                        DropdownMenuItem(text = { Text("Go to heading") }, leadingIcon = { Icon(Icons.Default.FormatListNumbered, null) }, onClick = {
                            actionMenuExpanded = false
                            showHeadingsDialog = true
                        })
                        DropdownMenuItem(text = { Text("Clear document") }, leadingIcon = { Icon(Icons.Default.DeleteSweep, null) }, onClick = {
                            actionMenuExpanded = false
                            showClearDialog = true
                        }, modifier = Modifier.testTag("clear_button"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            // Footer status line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${markdownText.trim().split(Regex("\\s+")).count { it.isNotEmpty() }} words · ${markdownText.length} characters",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )
                }
                
                // Byte and character counter
                Text("Saved on this device", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { triggerPrint() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("export_pdf_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = "Export PDF",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isTablet) {
                // Side-by-side view for Tablet Layouts
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                    ) {
                        Column {
                            FormattingToolbar(onInsert = { markdownText = insertAtEnd(markdownText, it) }, onFind = { showFindDialog = true })
                            EditorPane(text = markdownText, onTextChange = { markdownText = it }, search = findText, findSelection = findSelection)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        MarkdownPreview(text = markdownText)
                    }
                }
            } else {
                // Tab layout / top-bottom toggle for standard smartphones
                Column(modifier = Modifier.fillMaxSize()) {
                    // Geometric Tabs segment
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        TabHeader(
                            title = "Editor",
                            isActive = activeTab == 0,
                            onClick = { activeTab = 0 },
                            modifier = Modifier.weight(1f).testTag("editor_tab")
                        )
                        TabHeader(
                            title = "Preview",
                            isActive = activeTab == 1,
                            onClick = { activeTab = 1 },
                            modifier = Modifier.weight(1f).testTag("preview_tab")
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        if (activeTab == 0) {
                            Column {
                                FormattingToolbar(onInsert = { markdownText = insertAtEnd(markdownText, it) }, onFind = { showFindDialog = true })
                                EditorPane(text = markdownText, onTextChange = { markdownText = it }, search = findText, findSelection = findSelection)
                            }
                        } else {
                            MarkdownPreview(text = markdownText)
                        }
                    }
                }
            }
        }
    }

    if (showFindDialog) {
        AlertDialog(
            onDismissRequest = { showFindDialog = false },
            title = { Text("Find in document") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = findText, onValueChange = { findText = it }, label = { Text("Search text") }, singleLine = true)
                    Text(if (findText.isBlank()) "Enter a word or phrase" else "${Regex(Regex.escape(findText), RegexOption.IGNORE_CASE).findAll(markdownText).count()} matches")
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        if (findText.isNotBlank()) {
                            val next = markdownText.indexOf(findText, (findSelection ?: -1) + 1, ignoreCase = true)
                                .takeIf { it >= 0 } ?: markdownText.indexOf(findText, ignoreCase = true)
                            findSelection = next.takeIf { it >= 0 }
                            activeTab = 0
                        }
                    }) { Text("Next match") }
                    TextButton(onClick = { showFindDialog = false }) { Text("Done") }
                }
            }
        )
    }

    if (showHeadingsDialog) {
        val headings = remember(markdownText) { MarkdownParser.parse(markdownText).filterIsInstance<MarkdownBlock.Heading>() }
        AlertDialog(
            onDismissRequest = { showHeadingsDialog = false },
            title = { Text("Go to heading") },
            text = {
                if (headings.isEmpty()) Text("Add a Markdown heading to navigate this document.")
                else Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    headings.forEach { heading ->
                        TextButton(
                            onClick = {
                                findText = heading.text
                                findSelection = markdownText.indexOf(heading.text).takeIf { it >= 0 }
                                activeTab = 0
                                showHeadingsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(start = (12 + heading.level * 12).dp, end = 8.dp)
                        ) { Text(heading.text, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHeadingsDialog = false }) { Text("Close") } }
        )
    }

    // Verify clear Workspace text Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear workspace?") },
            text = { Text("All your progress in the editor will be wiped out. Are you sure you want to write a fresh markdown document?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        markdownText = ""
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TabHeader(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomBorderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "tabIndicator"
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        label = "tabText"
    )

    Box(
        modifier = modifier
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.background)
            .border(width = 0.dp, color = Color.Transparent)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                letterSpacing = 0.5.sp
            )
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(bottomBorderColor)
            )
        }
    }
}

private fun documentTitle(markdown: String): String = MarkdownParser.parse(markdown)
    .filterIsInstance<MarkdownBlock.Heading>().firstOrNull()?.text
    ?.take(60)?.replace(Regex("[^A-Za-z0-9 _-]"), "")?.ifBlank { null } ?: "Untitled"

private fun insertAtEnd(current: String, snippet: String): String =
    current + (if (current.isNotEmpty() && !current.endsWith("\n")) "\n" else "") + snippet

@Composable
private fun FormattingToolbar(onInsert: (String) -> Unit, onFind: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("H" to "## Heading", "B" to "**bold**", "I" to "*italic*", "List" to "- List item", "Quote" to "> Quote", "Code" to "```\ncode\n```").forEach { (label, snippet) ->
            TextButton(onClick = { onInsert(snippet) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Text(label) }
        }
        IconButton(onClick = onFind, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Search, "Find") }
    }
}

@Composable
fun EditorPane(
    text: String,
    onTextChange: (String) -> Unit,
    search: String = "",
    findSelection: Int? = null
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    var editorValue by remember { mutableStateOf(TextFieldValue(text)) }
    LaunchedEffect(text) {
        if (editorValue.text != text) editorValue = editorValue.copy(text = text, selection = TextRange(text.length))
    }
    LaunchedEffect(findSelection) {
        val start = findSelection
        if (start != null && start >= 0 && start + search.length <= editorValue.text.length) {
            editorValue = editorValue.copy(selection = TextRange(start, start + search.length))
            runCatching { focusRequester.requestFocus() }
        }
    }
    val lines = remember(text) { text.split("\n") }
    val lineCount = lines.size.coerceAtLeast(1)

    // Selection high contrast styling provider
    val customTextSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    )

    CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (search.isNotBlank()) {
            val count = remember(text, search) { Regex(Regex.escape(search), RegexOption.IGNORE_CASE).findAll(text).count() }
            Text("$count matches for ‘$search’", Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth().weight(1f)
                .verticalScroll(scrollState)
        ) {
            // Lines index sidebar - monospace numbers aligned perfectly with markdown input field lines
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
                    .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                    .padding(top = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = "$i",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        style = TextStyle(lineHeight = 22.sp)
                    )
                }
            }

            // Raw input textfield
            BasicTextField(
                value = editorValue,
                onValueChange = { editorValue = it; onTextChange(it.text) },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                    lineHeight = 22.sp
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 16.dp, bottom = 16.dp, start = 14.dp, end = 16.dp)
                    .fillMaxHeight()
                    .focusRequester(focusRequester)
                    .testTag("markdown_editor"),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false
                )
            )
        }
        }
    }
}

@Composable
fun MarkdownPreview(
    text: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (blocks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Awaiting markdown markup workspace editor...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                    fontStyle = FontStyle.Italic
                )
            }
        } else {
            for (block in blocks) {
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val headStyle = when (block.level) {
                            1 -> MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 23.sp
                            )
                            2 -> MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                            3 -> MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            else -> MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = block.text,
                            style = headStyle,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 4.dp)
                                .testTag("heading_${block.level}")
                        )
                    }
                    is MarkdownBlock.Paragraph -> {
                        MarkdownParagraph(text = block.text)
                    }
                    is MarkdownBlock.BlockQuote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                .padding(vertical = 4.dp)
                                .testTag("blockquote")
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                                    .align(Alignment.CenterVertically)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            MarkdownParagraph(
                                text = block.text,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MarkdownBlock.ListItem -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp)
                                .testTag("list_item")
                        ) {
                            Text(
                                text = block.checked?.let { if (it) "☑" else "□" } ?: if (block.ordered) "${block.index}." else "•",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(20.dp)
                            )
                            MarkdownParagraph(
                                text = block.text,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MarkdownBlock.CodeBlock -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                                .testTag("code_block")
                        ) {
                            if (block.language.isNotEmpty()) {
                                Text(
                                    text = block.language.uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            Text(
                                text = block.content,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFF1F5F9),
                                lineHeight = 18.sp
                            )
                        }
                    }
                    is MarkdownBlock.HorizontalRule -> {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            thickness = 1.dp,
                            modifier = Modifier
                                .padding(vertical = 10.dp)
                                .testTag("horizontal_rule")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownParagraph(
    text: String,
    modifier: Modifier = Modifier
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedString = remember(text) {
        buildAnnotatedString {
            val pattern = Regex("(\\*\\*.+?\\*\\*|__.+?__|\\*[^*]+\\*|_[^_]+_|`[^`]+`|\\[[^]]+\\]\\([^ )]+\\))")
            var cursor = 0
            for (match in pattern.findAll(text)) {
                append(text.substring(cursor, match.range.first))
                val raw = match.value
                when {
                    (raw.startsWith("**") && raw.endsWith("**")) || (raw.startsWith("__") && raw.endsWith("__")) ->
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(raw.substring(2, raw.length - 2)) }
                    raw.startsWith("*") || raw.startsWith("_") ->
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(raw.substring(1, raw.length - 1)) }
                    raw.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Color(0xFFA855F7), background = Color(0xFF1E293B).copy(alpha = 0.8f))) {
                        append(" ${raw.substring(1, raw.length - 1)} ")
                    }
                    raw.startsWith("[") -> {
                        val label = raw.substringAfter('[').substringBefore(']')
                        withStyle(SpanStyle(color = linkColor, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) { append(label) }
                    }
                }
                cursor = match.range.last + 1
            }
            append(text.substring(cursor))
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 14.sp,
            lineHeight = 22.sp
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}
