package com.gallopkeyboard.ime.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gallopkeyboard.ime.clipboard.ClipboardEntry
import com.gallopkeyboard.ime.theme.GallopColors
import com.gallopkeyboard.ime.theme.GallopVoiceTheme

/** Clipboard panel height matches typing keyboard for consistent IME sizing. */
val CLIPBOARD_PANEL_HEIGHT_DP: Dp = KEYBOARD_PANEL_HEIGHT_DP

/**
 * Full clipboard panel: pinned clips (persisted) + recent in-memory clips.
 *
 * Tap a row to insert; pin icon toggles persistence.
 */
@Composable
fun ClipboardPanel(
    pinnedEntries: List<ClipboardEntry>,
    recentTexts: List<String>,
    onInsert: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    isPinned: (String) -> Boolean,
    onClose: () -> Unit,
    panelHeight: Dp = CLIPBOARD_PANEL_HEIGHT_DP,
    modifier: Modifier = Modifier,
) {
    val pinnedTexts = pinnedEntries.map { it.text }.toSet()
    val recentEntries = recentTexts
        .filter { it !in pinnedTexts }
        .map { text ->
            ClipboardEntry(
                id = text,
                text = text,
                pinned = false,
                updatedAt = 0L,
            )
        }

    GallopVoiceTheme {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(panelHeight)
                .background(GallopColors.Surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 44.dp),
            ) {
                Text(
                    text = "Clipboard",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                if (pinnedEntries.isEmpty() && recentEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Copy text to see it here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (pinnedEntries.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Pinned",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                            items(pinnedEntries, key = { "pinned-${it.id}" }) { entry ->
                                ClipboardRow(
                                    text = entry.text,
                                    pinned = true,
                                    onInsert = onInsert,
                                    onTogglePin = { onTogglePin(entry.text) },
                                )
                            }
                            if (recentEntries.isNotEmpty()) {
                                item {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    Text(
                                        text = "Recent",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        items(recentEntries, key = { "recent-${it.id}" }) { entry ->
                            ClipboardRow(
                                text = entry.text,
                                pinned = isPinned(entry.text),
                                onInsert = onInsert,
                                onTogglePin = { onTogglePin(entry.text) },
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = "Back to keyboard",
                )
            }
        }
    }
}

@Composable
private fun ClipboardRow(
    text: String,
    pinned: Boolean,
    onInsert: (String) -> Unit,
    onTogglePin: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInsert(text) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(
            onClick = onTogglePin,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (pinned) "Unpin" else "Pin",
                tint = if (pinned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
