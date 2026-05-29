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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.markdown.MarkdownBlock
import com.example.markdown.MarkdownParser
import com.example.pdf.MarkdownPrintAdapter
import com.example.ui.theme.PurplePrintTheme
import java.io.BufferedReader
import java.io.InputStreamReader

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
    var markdownText by rememberSaveable { mutableStateOf(DEFAULT_MARKDOWN) }
    var showClearDialog by remember { mutableStateOf(false) }
    var activeTab by rememberSaveable { mutableStateOf(0) } // 0 = Editor, 1 = Preview

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
                        Toast.makeText(context, "Markdown parsed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error importing file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // PDF Printer Launcher
    val triggerPrint = {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "PurplePrint Document"
            val adapter = MarkdownPrintAdapter(context, markdownText, "PurplePrint_Export")
            printManager.print(jobName, adapter, null)
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
                    IconButton(
                        onClick = { filePickerLauncher.launch(arrayOf("text/plain", "text/markdown", "*/*")) },
                        modifier = Modifier.testTag("import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Import Markdown (.md)"
                        )
                    }
                    IconButton(
                        onClick = onThemeToggle,
                        modifier = Modifier.testTag("theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.testTag("clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Workspace",
                            tint = Color.Red.copy(alpha = 0.8f)
                        )
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
                    // Pulsating active heartbeat dot
                    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
                    val alphaState by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 1200
                                0.3f at 0
                                1.0f at 600
                                0.3f at 1200
                            },
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(alphaState)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF10B981)) // Emerald 500
                    )
                    Text(
                        text = "READY FOR EXPORT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )
                }
                
                // Byte and character counter
                val bytes = markdownText.toByteArray(Charsets.UTF_8).size
                Text(
                    text = "UTF-8 | $bytes BYTES",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
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
                        EditorPane(
                            text = markdownText,
                            onTextChange = { markdownText = it }
                        )
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
                            EditorPane(
                                text = markdownText,
                                onTextChange = { markdownText = it }
                            )
                        } else {
                            MarkdownPreview(text = markdownText)
                        }
                    }
                }
            }
        }
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

@Composable
fun EditorPane(
    text: String,
    onTextChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val lines = remember(text) { text.split("\n") }
    val lineCount = lines.size.coerceAtLeast(1)

    // Selection high contrast styling provider
    val customTextSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    )

    CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
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
                value = text,
                onValueChange = onTextChange,
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
                                text = if (block.ordered) "${block.index}." else "*",
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
    val annotatedString = remember(text) {
        buildAnnotatedString {
            val pattern = Regex("(\\*\\*.*?\\*\\*|\\*.*?\\*|`.*?`|[^*`]+)")
            val matches = pattern.findAll(text)
            for (match in matches) {
                val raw = match.value
                when {
                    raw.startsWith("**") && raw.endsWith("**") && raw.length >= 4 -> {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(raw.substring(2, raw.length - 2))
                        }
                    }
                    raw.startsWith("*") && raw.endsWith("*") && raw.length >= 2 -> {
                        withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(raw.substring(1, raw.length - 1))
                        }
                    }
                    raw.startsWith("`") && raw.endsWith("`") && raw.length >= 2 -> {
                        withStyle(style = SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFA855F7), // Amethyst purple fallback tint for preview text inline code
                            background = Color(0xFF1E293B).copy(alpha = 0.8f)
                        )) {
                            append(" " + raw.substring(1, raw.length - 1) + " ")
                        }
                    }
                    else -> {
                        append(raw)
                    }
                }
            }
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
