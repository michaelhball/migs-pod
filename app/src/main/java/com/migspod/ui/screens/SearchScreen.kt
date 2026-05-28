package com.migspod.ui.screens

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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.migspod.data.PodcastRepository
import com.migspod.data.SearchResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: PodcastRepository,
    onSubscribed: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var subscribingUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val subscribedPodcasts by repository.getAllPodcasts().collectAsStateWithLifecycle(emptyList())
    val subscribedUrls = remember(subscribedPodcasts) {
        subscribedPodcasts.map { it.feedUrl }.toSet()
    }

    fun doSearch() {
        if (query.isBlank()) return
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
        TopAppBar(title = { Text("Search Podcasts") })

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
                val isSubscribing = subscribingUrl == result.feedUrl

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                        IconButton(onClick = {
                            scope.launch {
                                subscribingUrl = result.feedUrl
                                repository.subscribeToPodcast(result.feedUrl)
                                subscribingUrl = null
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Subscribe")
                        }
                    }
                }
            }
        }
    }
}
