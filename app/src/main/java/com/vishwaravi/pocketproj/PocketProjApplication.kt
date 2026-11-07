package com.vishwaravi.pocketproj

import android.app.Application
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vishwaravi.pocketproj.data.HttpMetadataFetcher
import com.vishwaravi.pocketproj.data.LinkMetadataWorker
import com.vishwaravi.pocketproj.data.LinkRepository
import com.vishwaravi.pocketproj.data.MetadataEnqueuer
import com.vishwaravi.pocketproj.data.PocketDatabase
import com.vishwaravi.pocketproj.data.TransactionRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PocketProjApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: LinkRepository by lazy {
        val database = PocketDatabase.get(this)
        LinkRepository(
            dao = database.linkDao(),
            collectionDao = database.collectionDao(),
            lockerNoteDao = database.lockerNoteDao(),
            fetcher = HttpMetadataFetcher(),
            transactionRunner = TransactionRunner { block -> database.withTransaction(block) },
            enqueuer = MetadataEnqueuer { linkId, _ ->
                val request = OneTimeWorkRequestBuilder<LinkMetadataWorker>()
                    .setInputData(workDataOf(LinkMetadataWorker.KEY_LINK_ID to linkId))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                    .build()
                // Unique per link, replacing any earlier request. Plain enqueue() gave every tap
                // its own independent worker, so repeated taps piled up concurrent fetches for one
                // link, each incrementing the same metadataAttempts counter — the retry budget was
                // then spent on duplicate work instead of on genuine retries. REPLACE is also the
                // right policy for an explicit refresh: it drops any in-progress backoff schedule
                // and starts over, which is what asking again means.
                WorkManager.getInstance(this).enqueueUniqueWork(
                    "metadata-$linkId",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            },
            hiddenTagsProvider = {
                getSharedPreferences("pocket-proj-security", MODE_PRIVATE)
                    .getStringSet("hidden-tags", emptySet())
                    ?.toSet()
                    .orEmpty()
            }
        ).also { repository ->
            applicationScope.launch { repository.ensureInboxCollection() }
        }
    }
}