package com.migspod.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.migspod.MigsPodApp

class FeedRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = MigsPodApp.instance.repository.refreshAllPodcasts()
        return if (result.isSuccess) Result.success() else Result.retry()
    }
}
