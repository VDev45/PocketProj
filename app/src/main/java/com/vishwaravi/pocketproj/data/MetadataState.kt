package com.vishwaravi.pocketproj.data

/**
 * Lifecycle of the background metadata fetch attached to a saved link.
 * PENDING when queued or awaiting a retry, DONE when metadata was applied,
 * FAILED when the last attempt could not fetch. Manual refreshes reset to PENDING.
 */
enum class MetadataState {
    PENDING,
    DONE,
    FAILED
}