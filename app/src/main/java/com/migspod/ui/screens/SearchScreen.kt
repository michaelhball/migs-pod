package com.migspod.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.migspod.MigsPodApp as App
import com.migspod.data.PodcastRepository
import com.migspod.data.SearchResult
import com.migspod.data.looksLikeFeedUrl
import com.migspod.data.toUserMessage
import com.migspod.ui.components.AddFeedDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val searchResultsSaver = listSaver<List<SearchResult>, String>(
    save = { results -> results.flatMap { listOf(it.feedUrl, it.title, it.author, it.artworkUrl) } },
    restore = { flat -> flat.chunked(4) { (feedUrl, title, author, artworkUrl) -> SearchResult(feedUrl, title, author, artworkUrl) } },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: PodcastRepository,
    onResultClick: (feedUrl: String) -> Unit = {},
) {
    // Saveable so results survive switching tabs or opening a podcast and coming back.
    var query by rememberSaveable { mutableStateOf("") }
    var results by rememberSaveable(stateSaver = searchResultsSaver) { mutableStateOf(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // Non-null while the "Add by RSS URL" dialog is open (holds its prefilled text).
    var addFeedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var subscribingUrls by remember { mutableStateOf(emptySet<String>()) }
    val scope = rememberCoroutineScope()

    val subscribedPodcasts by remember { repository.getAllPodcasts() }.collectAsStateWithLifecycle(emptyList())
    val subscribedUrls = remember(subscribedPodcasts) {
        subscribedPodcasts.map { it.feedUrl }.toSet()
    }

    fun subscribe(feedUrl: String, openWhenDone: Boolean) {
        subscribingUrls = subscribingUrls + feedUrl
        // Run on the app scope so navigating away mid-fetch doesn't cancel the
        // subscribe (which would skip the episode insert).
        App.instance.appScope.launch {
            val result = repository.subscribeToPodcast(feedUrl)
            withContext(Dispatchers.Main) {
                subscribingUrls = subscribingUrls - feedUrl
                result
                    .onSuccess { if (openWhenDone) onResultClick(feedUrl) }
                    .onFailure { App.instance.showToast("Couldn't subscribe: ${it.toUserMessage()}") }
            }
        }
    }

    fun doSearch() {
        if (query.isBlank()) return
        // A pasted feed URL isn't a search term: offer to add it directly.
        if (looksLikeFeedUrl(query)) {
            addFeedUrl = query.trim()
            return
        }
        scope.launch {
            isSearching = true
            errorMessage = null
            repository.searchPodcasts(query).fold(
                onSuccess = { results = it },
                onFailure = { errorMessage = it.message }
            )
            isSearching = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Search Podcasts") },
            actions = {
                TextButton(onClick = { addFeedUrl = "" }) { Text("Add by URL") }
            },
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search for podcasts...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
        )

        Spacer(Modifier.height(8.dp))

        if (isSearching) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(32.dp)
            )
        }

        errorMessage?.let {
            Text(
                text = "Error: $it",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results, key = { it.feedUrl }, contentType = { "result" }) { result ->
                val isSubscribed = result.feedUrl in subscribedUrls
                val isSubscribing = result.feedUrl in subscribingUrls

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSubscribing) {
                            if (isSubscribed) onResultClick(result.feedUrl)
                            else subscribe(result.feedUrl, openWhenDone = true)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = result.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = result.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = result.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isSubscribed) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Subscribed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp),
                        )
                    } else if (isSubscribing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(4.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = { subscribe(result.feedUrl, openWhenDone = false) }) {
                            Icon(Icons.Default.Add, contentDescription = "Subscribe")
                        }
                    }
                }
            }
        }
    }

    addFeedUrl?.let { initialUrl ->
        AddFeedDialog(
            repository = repository,
            initialUrl = initialUrl,
            onDismiss = { addFeedUrl = null },
            onAdded = { feedUrl ->
                addFeedUrl = null
                onResultClick(feedUrl)
            },
        )
    }
}
