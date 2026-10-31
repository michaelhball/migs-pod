package com.migspod.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.migspod.MigsPodApp as App
import com.migspod.data.PodcastRepository
import com.migspod.data.normalizeFeedUrl
import com.migspod.data.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Subscribe straight from a feed URL, for shows that aren't in the search directory. */
@Composable
fun AddFeedDialog(
    repository: PodcastRepository,
    initialUrl: String,
    onDismiss: () -> Unit,
    onAdded: (feedUrl: String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun add() {
        if (busy) return
        val feedUrl = normalizeFeedUrl(url)
        if (feedUrl == null) {
            error = "That isn't a valid web address"
            return
        }
        busy = true
        error = null
        // App scope so rotating mid-fetch doesn't cancel the subscribe.
        App.instance.appScope.launch {
            val result = repository.subscribeToPodcast(feedUrl)
            withContext(Dispatchers.Main) {
                busy = false
                result
                    .onSuccess { onAdded(feedUrl) }
                    .onFailure { error = it.toUserMessage() }
            }
        }
    }

    val errorText = error
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add by RSS URL") },
        text = {
            Column {
                Text(
                    text = "For shows that aren't in search, like private or members-only feeds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    placeholder = { Text("https://example.com/feed.xml") },
                    singleLine = true,
                    enabled = !busy,
                    isError = errorText != null,
                    supportingText = errorText?.let { message -> @Composable { Text(message) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { add() }, enabled = !busy && url.isNotBlank()) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}
