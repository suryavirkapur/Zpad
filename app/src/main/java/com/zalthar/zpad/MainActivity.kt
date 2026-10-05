package com.zalthar.zpad

import android.content.Context
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.zalthar.zpad.ui.theme.ZpadTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a deletion removed, so it can be put back. A null id means it was never saved. */
private data class Removed(val id: Long?, val title: String, val content: String, val pinned: Boolean)

class MainActivity : ComponentActivity() {
    private lateinit var store: NoteStore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = NoteStore(applicationContext)
        setContent {
            val preferences = remember { getSharedPreferences("settings", Context.MODE_PRIVATE) }
            val previousTheme = remember { preferences.getString("theme", "Light") ?: "Light" }
            var theme by remember { mutableStateOf(when (previousTheme) { "Light" -> "Classic"; "Dark" -> "Slate"; else -> previousTheme }) }
            var appearance by remember { mutableStateOf(preferences.getString("appearance", null) ?: when {
                preferences.contains("darkAppearance") -> if (preferences.getBoolean("darkAppearance", false)) "Dark" else "Light"
                previousTheme in listOf("Dark", "Gruvbox", "Nord") -> "Dark"
                else -> "System"
            }) }
            val dark = when (appearance) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
            var font by remember { mutableStateOf(preferences.getString("font", "Monospace") ?: "Monospace") }
            var textSize by remember { mutableStateOf(preferences.getFloat("editorSize", 16f).coerceIn(12f, 28f)) }
            // Keep status bar icons readable when the app's mode differs from the system's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            ZpadTheme(theme, font, dark, textSize) {
                NotesApp(
                    theme = theme, font = font, appearance = appearance, dark = dark, editorSize = textSize,
                    changeTheme = { theme = it; preferences.edit().putString("theme", it).apply() },
                    changeFont = { font = it; preferences.edit().putString("font", it).apply() },
                    changeSize = { textSize = it.coerceIn(12f, 28f); preferences.edit().putFloat("editorSize", textSize).apply() },
                    changeAppearance = { appearance = it; preferences.edit().putString("appearance", it).apply() },
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun NotesApp(theme: String, font: String, appearance: String, dark: Boolean, changeTheme: (String) -> Unit, changeFont: (String) -> Unit, changeAppearance: (String) -> Unit, editorSize: Float, changeSize: (Float) -> Unit) {
        var notes by remember { mutableStateOf(emptyList<Note>()) }
        var menu by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
            var settings by rememberSaveable { mutableStateOf(false) }
        var pinned by rememberSaveable { mutableStateOf(false) }
        val shareBackground = MaterialTheme.colorScheme.surface.toArgb()
        val shareForeground = MaterialTheme.colorScheme.onSurface.toArgb()
        var loaded by remember { mutableStateOf(false) }
        var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
        var editing by rememberSaveable { mutableStateOf(false) }
        var freshNote by rememberSaveable { mutableStateOf(false) }
        var title by rememberSaveable { mutableStateOf("") }
        var content by rememberSaveable { mutableStateOf("") }
        var query by rememberSaveable { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var results by remember { mutableStateOf(emptyList<Note>()) }
        var completedQuery by remember { mutableStateOf("") }
        var searching by remember { mutableStateOf(false) }
        val focus = LocalFocusManager.current
        val snackbar = remember { SnackbarHostState() }
        val listState = rememberLazyListState()
        LaunchedEffect(query, notes, editing) {
            if (!editing) {
                searching = query.isNotBlank()
                try {
                    if (query.isNotBlank()) delay(180)
                    val searchQuery = query
                    val searchNotes = notes
                    val found = withContext(Dispatchers.IO) {
                        val context = kotlin.coroutines.coroutineContext
                        store.search(searchNotes, searchQuery) { context.ensureActive() }
                    }
                    results = found
                    completedQuery = query
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { error = "Couldn't search your notes. Please try again."; results = emptyList(); completedQuery = query }
                finally { searching = false }
            }
        }
        LaunchedEffect(error) {
            error?.let { snackbar.showSnackbar(it, withDismissAction = true); error = null }
        }
        fun resizeEditor(delta: Float) { changeSize((editorSize + delta).coerceIn(12f, 28f)) }
        var busy by remember { mutableStateOf(false) }
        var saved by rememberSaveable { mutableStateOf(true) }
        var revision by remember { mutableStateOf(0) }
        val saveMutex = remember { kotlinx.coroutines.sync.Mutex() }
        // A blank title borrows the note's first line, the way people name notes in their heads.
        fun resolvedTitle() = title.trim().ifEmpty {
            content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(60)?.trim() ?: "Untitled"
        }
        suspend fun refresh() {
            notes = withContext(Dispatchers.IO) { store.list() }
            loaded = true
        }

        suspend fun save() {
            saveMutex.lock()
            try {
                if (saved) return
                val currentRevision = revision
                val currentId = selectedId
                val currentTitle = resolvedTitle()
                val currentContent = content
                selectedId = withContext(Dispatchers.IO) {
                    store.save(currentId, currentTitle, currentContent)
                }
                saved = currentRevision == revision
            } finally {
                saveMutex.unlock()
            }
        }

        fun close() {
            lifecycleScope.launch {
                busy = true
                try {
                    if (title.isBlank() && content.isBlank()) {
                        // Nothing written: quietly drop the note instead of keeping an empty "Untitled".
                        saveMutex.lock()
                        try {
                            selectedId?.let { id -> withContext(Dispatchers.IO) { store.delete(id) } }
                            saved = true
                            selectedId = null
                        } finally { saveMutex.unlock() }
                    } else save()
                    refresh()
                    editing = false
                } catch (e: Exception) {
                    error = "Couldn't save this note. Please try again."
                } finally {
                    busy = false
                }
            }
        }

        fun restore(removed: Removed) {
            lifecycleScope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) {
                        val id = store.save(removed.id, removed.title, removed.content)
                        if (removed.pinned) store.pin(id, true)
                    }
                    refresh()
                } catch (e: Exception) { error = "Couldn't bring the note back." }
                finally { busy = false }
            }
        }

        fun deleteNote(targetId: Long?, fromEditor: Boolean) {
            lifecycleScope.launch {
                busy = true
                var removed: Removed? = null
                saveMutex.lock()
                try {
                    // An in-flight first save may have assigned the editor an ID meanwhile.
                    val id = if (fromEditor) selectedId else targetId
                    removed = if (fromEditor) Removed(id, resolvedTitle(), content, pinned).takeIf { id != null || content.isNotBlank() }
                        else id?.let { withContext(Dispatchers.IO) { store.read(it) } }?.let { Removed(it.id, it.title, it.content, it.pinned) }
                    id?.let { withContext(Dispatchers.IO) { store.delete(it) } }
                    if (fromEditor) { saved = true; editing = false; selectedId = null }
                    refresh()
                }
                catch (e: Exception) { removed = null; error = "Couldn't delete this note." }
                finally { saveMutex.unlock(); busy = false }
                removed?.let {
                    if (snackbar.showSnackbar("Note deleted", "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) restore(it)
                }
            }
        }

        fun openNew() {
            focus.clearFocus()
            pinned = false; selectedId = null; title = ""; content = ""; saved = true; freshNote = true; editing = true
        }

        LaunchedEffect(Unit) { try { refresh() } catch (e: Exception) { error = "Couldn't load your notes." } }
        LaunchedEffect(title, content, editing, revision) {
            if (editing && !saved) {
                delay(600)
                lifecycleScope.launch { try { save() } catch (e: Exception) { error = "Couldn't save this note. Please try again." } }
            }
        }
        val saveOnStop by rememberUpdatedState(newValue = {
            if (editing && !saved) lifecycleScope.launch {
                try { save() } catch (e: Exception) { error = "Couldn't save this note. Please try again." }
            }
        })
        DisposableEffect(Unit) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) saveOnStop()
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) lifecycleScope.launch {
                try {
                    val (name, text) = withContext(Dispatchers.IO) {
                        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
                        name to (contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Missing file"))
                    }
                    pinned = false; selectedId = null; title = name?.substringBeforeLast(".")?.take(60) ?: "Imported note"; content = text
                    revision++; saved = false; freshNote = false; editing = true; settings = false
                } catch (e: Exception) { error = "Couldn't open that file. Is it a plain text file?" }
            }
        }
        val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) lifecycleScope.launch {
                val exportContent = content
                try {
                    withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportContent) } ?: error("Missing file") }
                    snackbar.showSnackbar("Saved a copy")
                }
                catch (e: Exception) { error = "Couldn't save a copy of this note." }
            }
        }
        if (settings) {
            BackHandler { settings = false }
            SettingsPage(theme, font, appearance, dark, changeTheme, changeFont, changeAppearance, editorSize, changeSize,
                onImport = {
                    lifecycleScope.launch {
                        busy = true
                        try {
                            if (editing) save()
                            importer.launch(arrayOf("text/plain"))
                        } catch (e: Exception) {
                            error = "Couldn't save the current note before importing."
                            settings = false
                        } finally {
                            busy = false
                        }
                    }
                },
                importEnabled = !busy,
                onBack = { settings = false },
            )
            return
        }
        BackHandler(editing || query.isNotBlank()) {
            if (editing) {
                if (!busy) close()
            } else {
                query = ""
                focus.clearFocus()
            }
        }
        Scaffold(
            // The editor lays out its own bottom toolbar around the keyboard and navigation bar.
            contentWindowInsets = if (editing) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
            snackbarHost = { SnackbarHost(snackbar, if (editing) Modifier.imePadding().navigationBarsPadding().padding(bottom = 56.dp) else Modifier) },
            topBar = {
                if (editing) TopAppBar(
                    title = { },
                    navigationIcon = { IconButton(onClick = { if (!busy) close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to notes") } },
                    actions = {
                        Box {
                            IconButton(enabled = !busy, onClick = { shareMenu = true }) { Icon(Icons.Outlined.Share, "Share note") }
                            DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }, shape = MaterialTheme.shapes.medium,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 0.dp, tonalElevation = 0.dp) {
                                DropdownMenuItem(text = { Text("Share as text") }, onClick = {
                                    shareMenu = false
                                    lifecycleScope.launch { try { shareNote(resolvedTitle(), content, ShareKind.Text, font, shareBackground, shareForeground) } catch (e: Exception) { error = "Couldn't share this note." } }
                                })
                                DropdownMenuItem(text = { Text("Share as file") }, onClick = {
                                    shareMenu = false
                                    lifecycleScope.launch { try { shareNote(resolvedTitle(), content, ShareKind.File, font, shareBackground, shareForeground) } catch (e: Exception) { error = "Couldn't share this note." } }
                                })
                                DropdownMenuItem(text = { Text("Share as image") }, onClick = {
                                    shareMenu = false
                                    lifecycleScope.launch { try { shareNote(resolvedTitle(), content, ShareKind.Image, font, shareBackground, shareForeground) } catch (e: Exception) { error = "Couldn't share this note." } }
                                })
                            }
                        }
                        Box {
                            IconButton(onClick = { menu = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, "More options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 0.dp, tonalElevation = 0.dp) {
                                DropdownMenuItem(text = { Text(if (pinned) "Unpin" else "Pin") }, onClick = {
                                    menu = false
                                    lifecycleScope.launch {
                                        busy = true
                                        try { if (selectedId == null) saved = false; save(); val next = !pinned; withContext(Dispatchers.IO) { store.pin(selectedId!!, next) }; pinned = next; refresh() }
                                        catch (e: Exception) { error = "Couldn't pin this note." }
                                        finally { busy = false }
                                    }
                                })
                                DropdownMenuItem(text = { Text("Copy all") }, enabled = content.isNotEmpty(), onClick = {
                                    menu = false
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(resolvedTitle(), content))
                                })
                                DropdownMenuItem(text = { Text("Save a copy…") }, onClick = { menu = false; exporter.launch("${resolvedTitle()}.txt") })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Larger text") }, enabled = editorSize < 28f, onClick = { resizeEditor(2f) })
                                DropdownMenuItem(text = { Text("Smaller text") }, enabled = editorSize > 12f, onClick = { resizeEditor(-2f) })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleteNote(null, fromEditor = true) })
                            }
                        }
                    })
                else TopAppBar(
                    title = { Text("zpad", fontFamily = FontFamily(Font(R.font.lexend_bold, FontWeight.Bold)), fontWeight = FontWeight.Bold) },
                    actions = { IconButton(onClick = { focus.clearFocus(); settings = true }) { Icon(Icons.Outlined.Settings, "Settings") } },
                )
            },
            floatingActionButton = {
                if (!editing) FloatingActionButton(
                    onClick = { if (!busy) openNew() }, shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                ) { Icon(Icons.Default.Add, "New note") }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (editing) {
                    TextEditor(content, { content = it; revision++; saved = false }, Modifier.weight(1f), font, enabled = !busy, size = editorSize, saved = saved, autoFocus = freshNote, header = {
                        val titleStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface)
                        BasicTextField(
                            value = title,
                            onValueChange = { next -> val clean = next.replace("\n", " ").take(60); if (clean != title) { title = clean; revision++; saved = false } },
                            enabled = !busy, singleLine = true, textStyle = titleStyle,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                            decorationBox = { field ->
                                Box {
                                    if (title.isEmpty()) Text("Title", style = titleStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    field()
                                }
                            },
                        )
                    })
                } else {
                    if (notes.isNotEmpty() || query.isNotEmpty()) TextField(query, { query = it },
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = ""; focus.clearFocus() }) { Icon(Icons.Default.Close, "Clear search") } },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            focusedIndicatorColor = MaterialTheme.colorScheme.onSurface, unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
                    val pending = searching || completedQuery != query
                    val visible = if (query.isBlank()) notes else if (pending) emptyList() else results
                    if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    if (loaded && !pending && visible.isEmpty()) {
                        EmptyState(if (query.isBlank()) "No notes yet" else "No matches")
                    } else LazyColumn(state = listState, contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp)) {
                        items(visible, key = { it.id }) { note ->
                            SwipeNoteRow(pinned = note.pinned, enabled = !busy, onTogglePin = {
                                lifecycleScope.launch {
                                    busy = true
                                    try {
                                        withContext(Dispatchers.IO) { store.pin(note.id, !note.pinned) }
                                        refresh()
                                    } catch (e: Exception) {
                                        error = "Couldn't update the pin. Please try again."
                                    } finally {
                                        busy = false
                                    }
                                }
                            }, onDelete = { deleteNote(note.id, fromEditor = false) }) {
                                NoteCard(note, enabled = !busy) {
                                    lifecycleScope.launch {
                                        busy = true
                                        try {
                                            focus.clearFocus()
                                            val full = withContext(Dispatchers.IO) { store.read(note.id) }
                                            pinned = full.pinned; selectedId = full.id; title = full.title.takeUnless { it == "Untitled" } ?: ""; content = full.content
                                            saved = true; freshNote = false; editing = true
                                        }
                                        catch (e: Exception) { error = "Couldn't open this note." }
                                        finally { busy = false }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun friendlyDate(time: Long): String {
    val now = System.currentTimeMillis()
    return if (now - time < DateUtils.MINUTE_IN_MILLIS) "Just now"
        else DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}

@Composable
private fun NoteCard(note: Note, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (note.pinned) Icon(Icons.Filled.PushPin, "Pinned", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 10.dp).size(16.dp).rotate(45f))
        Text(note.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
        Text(remember(note.updated) { friendlyDate(note.updated) }.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun ColumnScope.EmptyState(message: String) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(message.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}
