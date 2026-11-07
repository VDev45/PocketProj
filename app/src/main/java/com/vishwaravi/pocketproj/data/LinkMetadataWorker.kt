package com.vishwaravi.pocketproj.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vishwaravi.pocketproj.PocketProjApplication

/**
 * Background metadata fetch for a saved link (REQUIREMENTS §3: save immediately,
 * fetch in the background, 30s budget). Retries with WorkManager backoff until
 * [MAX_ATTEMPTS] have been exhausted, then gives up and leaves the link PENDING/FAILED
 * for a manual refresh.
 */
class LinkMetadataWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val linkId = inputData.getLong(KEY_LINK_ID, -1L)
        if (linkId <= 0L) return Result.failure()

        val repository = (applicationContext as PocketProjApplication).repository
        if (repository.applyMetadata(linkId)) return Result.success()

        val attempts = repository.findById(linkId)?.metadataAttempts ?: Int.MAX_VALUE
        return if (attempts >= MAX_ATTEMPTS) Result.failure() else Result.retry()
    }

    companion object {
        const val KEY_LINK_ID = "linkId"
        const val MAX_ATTEMPTS = 3
    }
}