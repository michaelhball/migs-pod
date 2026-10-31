package com.migspod.ui

import com.migspod.MigsPodApp
import com.migspod.data.PodcastRepository
import com.migspod.data.toUserMessage
import com.migspod.player.PlayerController
import kotlinx.coroutines.launch

/** Downloads run on the app scope so they survive navigating away from the screen. */
fun startDownload(repository: PodcastRepository, episodeId: String) {
    val app = MigsPodApp.instance
    app.appScope.launch {
        repository.downloadEpisode(episodeId).onFailure {
            app.showToast("Download failed: ${it.toUserMessage()}")
        }
    }
}

fun deleteDownload(repository: PodcastRepository, episodeId: String) {
    // Stop first: the file is about to be removed out from under the player.
    PlayerController.stopIfCurrent(episodeId)
    MigsPodApp.instance.appScope.launch { repository.deleteDownload(episodeId) }
}
