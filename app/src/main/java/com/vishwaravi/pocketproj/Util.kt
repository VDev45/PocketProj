package com.vishwaravi.pocketproj

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

fun openInBrowser(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Toast.makeText(context, "No browser is available", Toast.LENGTH_SHORT).show() }
}