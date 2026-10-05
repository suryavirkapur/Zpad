package com.zalthar.zpad

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Horizontal gestures leave vertical list scrolling and row taps intact. */
@Composable
fun SwipeNoteRow(pinned: Boolean, enabled: Boolean, onTogglePin: () -> Unit, onDelete: () -> Unit, contentPadding: PaddingValues = PaddingValues(0.dp), content: @Composable () -> Unit) {
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val limit = with(LocalDensity.current) { 120.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val toggle by rememberUpdatedState(onTogglePin)
    val delete by rememberUpdatedState(onDelete)
    var drag by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val offset by animateFloatAsState(drag, if (dragging) snap() else spring(), label = "note swipe")
    val action = if (pinned) "Unpin" else "Pin"
    Box(Modifier.fillMaxWidth().semantics {
        if (enabled) customActions = listOf(
            CustomAccessibilityAction("$action note") { toggle(); true },
            CustomAccessibilityAction("Delete note") { delete(); true },
        )
    }.pointerInput(enabled, threshold, limit) {
        if (enabled) detectHorizontalDragGestures(
            onDragStart = { dragging = true },
            onDragCancel = { dragging = false; drag = 0f },
            onDragEnd = {
                val committed = abs(drag) >= threshold
                val swipedRight = drag > 0
                dragging = false
                drag = 0f
                if (committed) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (swipedRight) toggle() else delete()
                }
            },
            onHorizontalDrag = { change, amount ->
                change.consume()
                drag = (drag + amount).coerceIn(-limit, limit)
            },
        )
    }) {
        if (abs(offset) > 1f) {
            val deleting = offset < 0
            val tint = if (deleting) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
            Box(Modifier.matchParentSize().padding(contentPadding).background(if (deleting) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.align(if (offset > 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (deleting) Icons.Outlined.Delete else Icons.Outlined.PushPin, null, tint = tint, modifier = Modifier.size(20.dp))
                    Text(if (deleting) "Delete" else action, color = tint, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Column(Modifier.offset { IntOffset(offset.roundToInt(), 0) }.fillMaxWidth().padding(contentPadding).background(MaterialTheme.colorScheme.background)) {
            content()
        }
    }
}
