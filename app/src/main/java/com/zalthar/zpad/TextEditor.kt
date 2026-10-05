package com.zalthar.zpad

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private class NoteEditText(context: Context) : EditText(context) {
    var selectionChanged: ((Int, Int) -> Unit)? = null
    override fun onSelectionChanged(start: Int, end: Int) {
        super.onSelectionChanged(start, end)
        selectionChanged?.invoke(start, end)
    }
}

@Composable
fun TextEditor(text: String, onChange: (String) -> Unit, modifier: Modifier, font: String, enabled: Boolean, size: Float, saved: Boolean, autoFocus: Boolean = false, header: @Composable () -> Unit = {}) {
    var editor by remember { mutableStateOf<NoteEditText?>(null) }
    var find by rememberSaveable { mutableStateOf("") }
    var finding by rememberSaveable { mutableStateOf(false) }
    var selectionStart by rememberSaveable { mutableStateOf(0) }
    var selectionEnd by rememberSaveable { mutableStateOf(0) }
    var matches by remember { mutableStateOf(emptyList<Int>()) }
    var searching by remember { mutableStateOf(false) }
    var words by remember { mutableStateOf(0) }
    var lines by remember { mutableStateOf(1) }
    var details by rememberSaveable { mutableStateOf(false) }
    val findFocus = remember { FocusRequester() }
    LaunchedEffect(finding) { if (finding) findFocus.requestFocus() }
    val change by rememberUpdatedState(onChange)
    val color = MaterialTheme.colorScheme.onSurface
    LaunchedEffect(text) {
        delay(200)
        val counts = withContext(Dispatchers.Default) {
            var wordCount = 0; var lineCount = 1; var inWord = false
            text.forEachIndexed { index, char ->
                if (index % 8192 == 0) ensureActive()
                if (char == '\n') lineCount++
                if (char.isWhitespace()) inWord = false else if (!inWord) { wordCount++; inWord = true }
            }
            wordCount to lineCount
        }
        words = counts.first; lines = counts.second
    }
    LaunchedEffect(text, find, finding) {
        matches = emptyList()
        if (finding && find.isNotEmpty()) {
            searching = true
            try {
                delay(150)
                matches = withContext(Dispatchers.Default) {
                    buildList {
                        var offset = 0
                        while (offset <= text.length - find.length) {
                            ensureActive()
                            val next = text.indexOf(find, offset, ignoreCase = true)
                            if (next < 0) break
                            add(next); offset = next + find.length
                        }
                    }
                }
            } finally { searching = false }
        } else searching = false
    }
    fun navigate(forward: Boolean) {
        if (matches.isEmpty()) return
        editor?.let { view ->
            val next = if (forward) matches.firstOrNull { it >= view.selectionEnd } ?: matches.first()
                else matches.lastOrNull { it < view.selectionStart } ?: matches.last()
            view.requestFocus()
            view.setSelection(next, (next + find.length).coerceAtMost(view.length()))
        }
    }
    Column(modifier.fillMaxWidth().imePadding()) {
        header()
        AnimatedVisibility(finding) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(find, { find = it }, singleLine = true, placeholder = { Text("Find in note") }, modifier = Modifier.weight(1f).focusRequester(findFocus),
                    shape = MaterialTheme.shapes.extraLarge, leadingIcon = { Icon(Icons.Default.Search, null) },
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { navigate(true) }),
                    supportingText = {
                        val selectedMatch = matches.indexOf(selectionStart).takeIf { selectionEnd - selectionStart == find.length } ?: -1
                        Text(if (searching) "Searching…" else if (find.isEmpty()) "Type a word or phrase" else if (matches.isEmpty()) "No matches" else if (selectedMatch >= 0) "${selectedMatch + 1} of ${matches.size}" else if (matches.size == 1) "1 match" else "${matches.size} matches")
                    })
                IconButton(enabled = matches.isNotEmpty() && !searching, onClick = { navigate(false) }) { Icon(Icons.Default.KeyboardArrowUp, "Previous match") }
                IconButton(enabled = matches.isNotEmpty() && !searching, onClick = { navigate(true) }) { Icon(Icons.Default.KeyboardArrowDown, "Next match") }
                IconButton(onClick = { finding = false; find = "" }) { Icon(Icons.Default.Close, "Close find") }
            }
        }
        AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
            NoteEditText(context).apply {
                gravity = Gravity.TOP or Gravity.START
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setLineSpacing(0f, 1.35f)
                val density = resources.displayMetrics.density
                setPadding((20 * density).toInt(), (8 * density).toInt(), (20 * density).toInt(), (48 * density).toInt())
                hint = "Start writing…"
                if (autoFocus) post {
                    requestFocus()
                    context.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(this, 0)
                }
                setText(text)
                setSelection(selectionStart.coerceIn(0, length()), selectionEnd.coerceIn(0, length()))
                selectionChanged = { start, end -> selectionStart = start.coerceAtLeast(0); selectionEnd = end.coerceAtLeast(0) }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { change(s.toString()) }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                editor = this
            }
        }, update = { view ->
            val face = when (font) { "Serif" -> Typeface.SERIF; "Sans serif" -> Typeface.SANS_SERIF; else -> Typeface.MONOSPACE }
            if (view.typeface != face) view.typeface = face
            if (view.tag != size) { view.textSize = size; view.tag = size }
            view.isEnabled = enabled
            view.setTextColor(color.toArgb())
            view.setHintTextColor(color.copy(alpha = 0.5f).toArgb())
        })
        Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = enabled, onClick = { editor?.onTextContextMenuItem(android.R.id.undo) }) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                IconButton(enabled = enabled, onClick = { editor?.onTextContextMenuItem(android.R.id.redo) }) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                IconButton(enabled = enabled, onClick = { editor?.let { view ->
                    val start = view.selectionStart.coerceAtLeast(0); val end = view.selectionEnd.coerceAtLeast(start)
                    view.text.replace(start, end, "    ")
                } }) { Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Indent") }
                IconToggleButton(checked = finding, onCheckedChange = { finding = it; if (!it) find = "" }) { Icon(Icons.Default.Search, if (finding) "Close find" else "Find in note") }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = "Show more counts") { details = !details }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(if (details) "$lines ${if (lines == 1) "line" else "lines"} · ${text.length} chars" else "$words ${if (words == 1) "word" else "words"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (saved) "Saved" else "Saving…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}
