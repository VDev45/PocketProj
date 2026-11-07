package com.vishwaravi.pocketproj

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vishwaravi.pocketproj.data.DuplicateImportMode
import com.vishwaravi.pocketproj.data.ImportLink
import com.vishwaravi.pocketproj.data.LinkEntity
import com.vishwaravi.pocketproj.data.LinkRepository
import com.vishwaravi.pocketproj.data.UrlValidator
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Sentinel stored in "hidden-session-until" for the "Immediately" lock timeout. Negative, so it can
 * never collide with a real epoch-millis expiry. It marks a session that stays valid for the rest of
 * the current foreground period: the lifecycle observer in [PocketAppRoot] drops it on ON_STOP and on
 * ON_START, so leaving the app always re-prompts for the PIN.
 */
internal const val FOREGROUND_SESSION = -1L

/**
 * Whether a stored "hidden-session-until" value authorises access right now. The [FOREGROUND_SESSION]
 * sentinel is valid for the rest of this foreground period; every other stored value is a wall-clock
 * expiry compared against [now].
 */
internal fun isHiddenSessionValid(expiry: Long, now: Long): Boolean =
    expiry == FOREGROUND_SESSION || expiry > now

/**
 * Whether a stored session survives the process starting up again. [FOREGROUND_SESSION] never does:
 * it only ever meant "valid while in the foreground", and a freshly started process has not been in
 * the foreground yet. This is deliberately not the same question as [isHiddenSessionValid] — for the
 * sentinel the two answers differ, which is exactly the case that has to keep locking.
 */
internal fun sessionSurvivesRestart(expiry: Long, now: Long): Boolean =
    expiry != FOREGROUND_SESSION && expiry > now

/**
 * Encapsulates PIN entry logic: accept digits, biometric, lockout, session creation.
 * Used by both PrivacySecurityScreen and PinGateDialog to avoid duplication.
 */
class PinEntryController(
    private val preferences: SharedPreferences,
    private val context: Context,
    /**
     * Whether a successful PIN should also open a hidden-content session. False for callers that
     * use the PIN purely as an authorisation gate (including hidden links in an export), so that
     * authorising one thing does not quietly unlock another.
     */
    private val startSessionOnVerify: Boolean = true,
    private val onVerified: () -> Unit
) {
    var protectedPin: String? by mutableStateOf(preferences.getString("pin-verifier", null))
    var legacyPinHash: String? by mutableStateOf(preferences.getString("pin-hash", null))
    var legacyPinSalt: String? by mutableStateOf(preferences.getString("pin-salt", null))
    var pin by mutableStateOf("")
    var pinMessage: String? by mutableStateOf(null)
    var pinVerified by mutableStateOf(false)
    /** First entry stays in memory only until the user confirms it. */
    private var pendingSetupPin: String? = null
    var setupStep by mutableStateOf(1)
    val isCreatingPin: Boolean
        get() = protectedPin == null && legacyPinHash == null
    var failedAttempts by mutableStateOf(preferences.getInt("pin-failed-attempts", 0))
    var lockedUntil by mutableStateOf(preferences.getLong("pin-locked-until", 0L))
    private val useBiometrics = preferences.getBoolean("biometrics", true)

    /**
     * Read fresh on every call: the lock-timeout dropdown can be changed while this controller is
     * alive, so a value captured at construction goes stale.
     */
    private fun sessionTimeoutMillis(): Long =
        when (preferences.getString("lock-timeout", "1 minute") ?: "1 minute") {
            "Immediately" -> 0L
            "30 seconds" -> 30_000L
            "1 minute" -> 60_000L
            "5 minutes" -> 300_000L
            "15 minutes" -> 900_000L
            else -> 60_000L
        }

    /**
     * Opens a hidden-content session. "Immediately" means "valid until the app is backgrounded",
     * which is what the ON_STOP branch in [PocketAppRoot] already implements — it drops the session
     * outright. Writing `now + 0` here instead would store a timestamp that is already in the past,
     * so the very next validity check would reject the session the user had just unlocked.
     */
    fun startSession() {
        val timeout = sessionTimeoutMillis()
        preferences.edit().putLong(
            "hidden-session-until",
            if (timeout == 0L) FOREGROUND_SESSION else System.currentTimeMillis() + timeout
        ).apply()
    }

    private fun completeVerification() {
        if (startSessionOnVerify) startSession()
        onVerified()
    }

    fun acceptDigit(digit: String) {
        if (digit.length != 1 || digit[0] !in '0'..'9') return
        if (pin.length >= 4 || System.currentTimeMillis() < lockedUntil) return
        val updated = pin + digit
        pin = updated
        pinMessage = null
        if (updated.length == 4) {
            if (isCreatingPin) {
                if (setupStep == 1) {
                    // Do not persist an unconfirmed PIN. Keep it only for this setup session.
                    pendingSetupPin = updated
                    setupStep = 2
                    pin = ""
                    pinMessage = "Now enter the same PIN to confirm it."
                } else if (updated == pendingSetupPin) {
                    protectedPin = protectPinVerifier(updated)
                    preferences.edit()
                        .putString("pin-verifier", protectedPin)
                        .putInt("pin-failed-attempts", 0)
                        .putLong("pin-locked-until", 0L)
                        .apply()
                    pendingSetupPin = null
                    pin = ""
                    pinVerified = true
                    pinMessage = "Your locker is secured."
                    Toast.makeText(context, "Hidden Locker secured", Toast.LENGTH_SHORT).show()
                    completeVerification()
                } else {
                    pin = ""
                    pinVerified = false
                    pinMessage = "Those PINs don't match. Try again."
                }
            } else {
                val valid = if (protectedPin != null) {
                    verifyProtectedPin(updated, protectedPin.orEmpty())
                } else {
                    hashPin(updated, legacyPinSalt.orEmpty()) == legacyPinHash
                }
                if (valid) {
                    if (protectedPin == null) {
                        protectedPin = protectPinVerifier(updated)
                        preferences.edit()
                            .remove("pin-hash")
                            .remove("pin-salt")
                            .putString("pin-verifier", protectedPin)
                            .apply()
                        legacyPinHash = null
                        legacyPinSalt = null
                    }
                    failedAttempts = 0
                    lockedUntil = 0L
                    preferences.edit()
                        .putInt("pin-failed-attempts", 0)
                        .putLong("pin-locked-until", 0L)
                        .apply()
                    pin = ""
                    pinVerified = true
                    pinMessage = "PIN verified"
                    completeVerification()
                } else {
                    val nextAttempts = failedAttempts + 1
                    val lockout = if (nextAttempts >= 5) System.currentTimeMillis() + 30_000L else 0L
                    failedAttempts = if (lockout > 0L) 0 else nextAttempts
                    lockedUntil = lockout
                    preferences.edit()
                        .putInt("pin-failed-attempts", failedAttempts)
                        .putLong("pin-locked-until", lockedUntil)
                        .apply()
                    pin = ""
                    pinVerified = false
                    pinMessage = if (lockout > 0L) "Too many attempts. Try again in 30 seconds." else "Incorrect PIN. Try again."
                }
            }
        }
    }

    fun onBiometricClick() {
        if (isCreatingPin) {
            pinMessage = "Create and confirm a PIN before using biometric unlock."
        } else if (!useBiometrics) {
            pinMessage = "Biometric unlock is disabled"
        } else if (protectedPin == null && legacyPinHash == null) {
            pinMessage = "Create a PIN before using biometric unlock"
        } else {
            val activity = context as? FragmentActivity
            if (activity == null) {
                pinMessage = "Biometric unlock is unavailable"
            } else {
                val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
                when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
                    BiometricManager.BIOMETRIC_SUCCESS -> {
                        val prompt = BiometricPrompt(
                            activity,
                            // Context.getMainExecutor is API 28; minSdk here is 26. Calling it
                            // directly threw NoSuchMethodError on Android 8.0 and 8.1 -- so the
                            // biometric unlock button, the whole point of the Hidden section, was
                            // a crash on those devices rather than a prompt. ContextCompat is the
                            // backport and does the same thing on every supported level.
                            ContextCompat.getMainExecutor(activity),
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    pinVerified = true
                                    pinMessage = "Biometric authentication succeeded"
                                    completeVerification()
                                }

                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    pinVerified = false
                                    pinMessage = errString.toString()
                                }

                                override fun onAuthenticationFailed() {
                                    pinVerified = false
                                    pinMessage = "Biometric authentication failed"
                                }
                            }
                        )
                        prompt.authenticate(
                            BiometricPrompt.PromptInfo.Builder()
                                .setTitle("Unlock Hidden content")
                                .setSubtitle("Authenticate to reveal your private links")
                                .setNegativeButtonText("Use PIN")
                                .setAllowedAuthenticators(authenticators)
                                .build()
                        )
                    }
                    else -> pinMessage = "No supported biometric is enrolled"
                }
            }
        }
    }

    fun onDeleteClick() {
        pin = pin.dropLast(1)
        pinMessage = null
    }

    fun refreshFromPrefs() {
        protectedPin = preferences.getString("pin-verifier", null)
        legacyPinHash = preferences.getString("pin-hash", null)
        legacyPinSalt = preferences.getString("pin-salt", null)
        failedAttempts = preferences.getInt("pin-failed-attempts", 0)
        lockedUntil = preferences.getLong("pin-locked-until", 0L)
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Import / Export
   ═══════════════════════════════════════════════════════════════════════════ */

@Composable
fun ImportExportScreen(
    repository: LinkRepository,
    links: List<LinkEntity>,
    collectionNameById: Map<Long, String>,
    securityPreferences: android.content.SharedPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<List<ImportLink>?>(null) }
    var duplicateCount by remember { mutableStateOf(0) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var statusIsError by rememberSaveable { mutableStateOf(false) }
    var duplicateMode by rememberSaveable { mutableStateOf(DuplicateImportMode.SKIP) }

    // Export gating (REQUIREMENTS §8). A format is only remembered while the user is being asked
    // about hidden links or is entering their PIN; the pickers fire once that is settled.
    val hasHiddenLinks = remember(links) { links.any { it.isHidden } }
    var exportFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var includeHidden by remember { mutableStateOf(false) }
    var showHiddenChoice by remember { mutableStateOf(false) }
    var showExportPin by remember { mutableStateOf(false) }

    // Read inside the launcher callbacks, which Compose keeps current via rememberUpdatedState.
    val exportPayload = remember(links, includeHidden) {
        exportableLinks(links, includeHidden)
    }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val saved = writeText(context, uri, exportPayload.toJson(collectionNameById))
            status = if (saved) "JSON export created" else "Could not write the export file"
            statusIsError = !saved
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val saved = writeText(context, uri, exportPayload.toCsv(collectionNameById))
            status = if (saved) "CSV export created" else "Could not write the export file"
            statusIsError = !saved
        }
    }

    fun beginExport(format: ExportFormat) {
        if (hasHiddenLinks) {
            exportFormat = format
            showHiddenChoice = true
        } else {
            // Nothing hidden to ask about, so go straight to the destination picker.
            includeHidden = false
            if (format == ExportFormat.JSON) {
                exportJson.launch("pocket-proj-backup.json")
            } else {
                exportCsv.launch("pocket-proj-backup.csv")
            }
        }
    }

    fun launchExportPicker() {
        when (exportFormat) {
            ExportFormat.JSON -> exportJson.launch("pocket-proj-backup.json")
            ExportFormat.CSV -> exportCsv.launch("pocket-proj-backup.csv")
            null -> Unit
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val parsed = runCatching {
            parseJson(readText(context, uri))
        }.getOrElse {
            status = "Unable to read that backup file"
            statusIsError = true
            emptyList()
        }
        if (parsed.isEmpty()) {
            if (status == null) status = "No valid links were found in that file"
            return@rememberLauncherForActivityResult
        }
        duplicateCount = parsed.count { imported ->
            val canonical = UrlValidator.canonicalizeOrNull(imported.url)
            links.any { it.url == canonical || it.url.equals(imported.url.trim(), ignoreCase = true) }
        }
        pendingImport = parsed
        duplicateMode = DuplicateImportMode.SKIP
        if (duplicateCount == 0) {
            scope.launch {
                repository.importLinks(parsed, DuplicateImportMode.SKIP)
                    .onSuccess { count ->
                        status = "Imported $count links"
                        statusIsError = false
                    }
                    .onFailure {
                        status = it.message ?: "Unable to import backup"
                        statusIsError = true
                    }
                pendingImport = null
            }
        }
    }

    PocketScreen(blobs = listOf(PocketColors.BlobMint, PocketColors.BlobLilac, PocketColors.BlobAmber)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PocketSpace.lg, vertical = PocketSpace.sm),
            verticalArrangement = Arrangement.spacedBy(PocketSpace.sm)
        ) {
            PocketPageHeader("Import / Export", onBack, eyebrow = "Your data")

            // Hero: the payload size, stated as a number rather than a label.
            PocketGroup(shape = PocketShape.xxl) {
                Row(
                    Modifier.padding(PocketSpace.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            links.size.toString(),
                            style = PocketType.numeric,
                            color = PocketColors.Accent
                        )
                        Text(
                            if (links.size == 1) "link in this export" else "links in this export",
                            style = PocketType.bodySmall,
                            color = PocketColors.InkMuted
                        )
                    }
                    PocketIconBadge(shape = PocketShape.blob, size = 58.dp, accent = PocketColors.Sage) {
                        PocketOutboundGlyph(tint = PocketColors.Sage, modifier = Modifier.size(24.dp))
                    }
                }
            }

            PocketSectionLabel("Export data")
            Row(
                horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                ExportActionButton("JSON", "Full backup", Modifier.weight(1f), PocketColors.Accent) {
                    beginExport(ExportFormat.JSON)
                }
                ExportActionButton("CSV", "Spreadsheet", Modifier.weight(1f), PocketColors.Sage) {
                    beginExport(ExportFormat.CSV)
                }
            }

            PocketSectionLabel("Import data")
            PocketCardSurface(shape = PocketShape.xl) {
                ExportRow(
                    title = "Restore a backup",
                    subtitle = "Reads a Voket JSON export",
                    onClick = { importFile.launch(arrayOf("application/json")) }
                )
            }

            status?.let {
                PocketBanner(
                    message = it,
                    icon = if (statusIsError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    accent = if (statusIsError) PocketColors.Danger else PocketColors.Sage,
                    container = if (statusIsError) PocketColors.DangerSoft else PocketColors.SageSoft,
                    onAccent = if (statusIsError) PocketColors.OnDangerSoft else PocketColors.OnSageSoft
                )
            }

            Spacer(Modifier.height(PocketSpace.lg))
            Text(
                "Exports are written to a location you choose. Nothing leaves this device on its own.",
                style = PocketType.caption,
                color = PocketColors.InkFaint,
                modifier = Modifier.padding(horizontal = PocketSpace.xs)
            )
        }
    }

    pendingImport?.takeIf { duplicateCount > 0 }?.let { imported ->
        DuplicateImportDialog(
            duplicateCount = duplicateCount,
            mode = duplicateMode,
            onModeChange = { duplicateMode = it },
            onDismiss = { pendingImport = null },
            onContinue = {
                scope.launch {
                    repository.importLinks(imported, duplicateMode)
                        .onSuccess { count ->
                            status = "Imported or updated $count links"
                            statusIsError = false
                        }
                        .onFailure {
                            status = it.message ?: "Unable to import backup"
                            statusIsError = true
                        }
                    pendingImport = null
                }
            }
        )
    }

    if (showHiddenChoice) {
        IncludeHiddenLinksDialog(
            hiddenCount = links.count { it.isHidden },
            onInclude = {
                showHiddenChoice = false
                showExportPin = true
            },
            onExclude = {
                showHiddenChoice = false
                includeHidden = false
                launchExportPicker()
            },
            onDismiss = {
                showHiddenChoice = false
                exportFormat = null
            }
        )
    }

    if (showExportPin) {
        PinGateDialog(
            preferences = securityPreferences,
            onDismiss = {
                showExportPin = false
                exportFormat = null
            },
            onUnlocked = {
                showExportPin = false
                includeHidden = true
                launchExportPicker()
            },
            title = "Include hidden links",
            startSessionOnVerify = false
        )
    }
}

/** Formats offered by the export buttons. */
private enum class ExportFormat { JSON, CSV }

/** A tonal export target. Two side by side, distinguished only by accent. */
@Composable
private fun ExportActionButton(
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
    accent: Color = PocketColors.Accent,
    onClick: () -> Unit
) {
    Box(modifier.pressScale(0.97f)) {
        Surface(
            onClick = onClick,
            shape = PocketShape.lg,
            color = accent.copy(alpha = 0.12f),
            contentColor = accent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = PocketSpace.md, vertical = PocketSpace.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm)
            ) {
                PocketIconBadge(
                    shape = PocketShape.squircle,
                    size = 38.dp,
                    accent = accent,
                    container = accent.copy(alpha = 0.18f)
                ) {
                    PocketOutboundGlyph(tint = accent, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = PocketType.titleSmall,
                        color = PocketColors.Ink,
                        maxLines = 1
                    )
                    Text(
                        caption,
                        style = PocketType.caption,
                        color = PocketColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportRow(title: String, subtitle: String, onClick: () -> Unit) {
    PocketListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leading = {
            PocketIconBadge(shape = PocketShape.squircle, accent = PocketColors.Accent) {
                PocketImportGlyph(tint = PocketColors.Accent)
            }
        },
        trailing = { PocketChevron() }
    )
}

/**
 * REQUIREMENTS §8: every export asks whether hidden links should be included. Only shown when there
 * is something hidden to ask about — offering the choice with nothing behind it would be noise.
 */
@Composable
private fun IncludeHiddenLinksDialog(
    hiddenCount: Int,
    onInclude: () -> Unit,
    onExclude: () -> Unit,
    onDismiss: () -> Unit
) {
    PocketDialog(
        onDismissRequest = onDismiss,
        title = "Include hidden links?",
        body = "$hiddenCount hidden link${if (hiddenCount == 1) "" else "s"} will be left out of this " +
            "export. Including ${if (hiddenCount == 1) "it" else "them"} asks for your PIN, and the file " +
            "will contain links the Hidden Locker normally keeps private.",
        icon = Icons.Filled.Lock,
        confirmLabel = "Include",
        onConfirm = onInclude,
        dismissLabel = "Leave them out",
        onDismissAction = onExclude
    )
}

@Composable
private fun DuplicateImportDialog(
    duplicateCount: Int,
    mode: DuplicateImportMode,
    onModeChange: (DuplicateImportMode) -> Unit,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    PocketDialog(
        onDismissRequest = onDismiss,
        title = "Duplicates found",
        body = "$duplicateCount matching link${if (duplicateCount == 1) "" else "s"} already in your library. Choose what to do with them.",
        icon = Icons.Filled.Warning,
        confirmLabel = "Continue",
        onConfirm = onContinue,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(PocketSpace.xs)) {
                DuplicateChoice("Skip duplicates", DuplicateImportMode.SKIP, mode, onModeChange)
                DuplicateChoice("Overwrite existing", DuplicateImportMode.OVERWRITE, mode, onModeChange)
                DuplicateChoice("Keep both", DuplicateImportMode.KEEP_BOTH, mode, onModeChange)
            }
        }
    )
}

@Composable
private fun DuplicateChoice(
    title: String,
    value: DuplicateImportMode,
    selected: DuplicateImportMode,
    onSelect: (DuplicateImportMode) -> Unit
) {
    val active = selected == value
    val container by animateColorAsState(
        if (active) PocketColors.AccentSoft else Color.Transparent,
        animationSpec = pocketTween(PocketMotion.normal),
        label = "duplicateContainer"
    )
    Box(Modifier.fillMaxWidth().pressScale(0.985f)) {
        Surface(
            onClick = { onSelect(value) },
            shape = PocketShape.md,
            color = container,
            contentColor = PocketColors.Ink,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = PocketSpace.md, vertical = PocketSpace.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm)
            ) {
                PocketCheckMark(checked = active)
                Text(
                    title,
                    style = PocketType.bodyStrong,
                    color = if (active) PocketColors.OnAccentSoft else PocketColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Appearance
   ═══════════════════════════════════════════════════════════════════════════ */

@Composable
fun AppearanceScreen(
    mode: String,
    materialYou: Boolean,
    onBack: () -> Unit,
    onModeChange: (String) -> Unit,
    onMaterialYouChange: (Boolean) -> Unit
) {
    PocketScreen(blobs = listOf(PocketColors.BlobLilac, PocketColors.BlobAmber, PocketColors.BlobMint)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PocketSpace.lg, vertical = PocketSpace.sm),
            verticalArrangement = Arrangement.spacedBy(PocketSpace.md)
        ) {
            PocketPageHeader("Appearance", onBack, eyebrow = "Settings")

            PocketSectionLabel("Theme")
            Text(
                when (mode) {
                    "Dark" -> "Always use the Voket dark palette."
                    "Material You" -> "Use your device wallpaper colours."
                    else -> "Use the standard Voket light palette."
                },
                style = PocketType.bodySmall,
                color = PocketColors.InkMuted
            )
            PocketSegmentedControl(
                options = listOf("Default", "Dark", "Material You"),
                selected = mode,
                label = { it },
                onSelect = onModeChange
            )

            PocketSectionLabel("Preview")
            Row(
                horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                ThemePreviewCard(
                    title = "Default",
                    scheme = VoketLight,
                    selected = mode == "Default",
                    onClick = { onModeChange("Default") },
                    modifier = Modifier.weight(1f)
                )
                ThemePreviewCard(
                    title = "Dark",
                    scheme = VoketDark,
                    selected = mode == "Dark",
                    onClick = { onModeChange("Dark") },
                    modifier = Modifier.weight(1f)
                )
                // The third mode had no preview at all, so with Material You active *neither*
                // card showed as selected and the section could not switch to it. This one draws
                // the live scheme: the wallpaper palette once it is applied, and the Voket palette
                // before that — which is the same fallback the app itself uses on older devices,
                // so it is never further from the truth than the running app is.
                ThemePreviewCard(
                    title = "Material You",
                    scheme = MaterialTheme.colorScheme,
                    selected = mode == "Material You",
                    onClick = { onModeChange("Material You") },
                    modifier = Modifier.weight(1f)
                )
            }
            if (mode == "Material You") {
                PocketBanner(
                    message = "Material You tints the same palette from your wallpaper.",
                    icon = Icons.Filled.CheckCircle,
                    accent = PocketColors.Sage,
                    container = PocketColors.SageSoft,
                    onAccent = PocketColors.OnSageSoft
                )
            }

            PocketSectionLabel("Dynamic colour")
            PocketGroup(shape = PocketShape.xl) {
                Row(
                    Modifier.padding(horizontal = PocketSpace.md, vertical = PocketSpace.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Material You", style = PocketType.bodyStrong, color = PocketColors.Ink)
                        Text(
                            "Dynamic colours from your Android system",
                            style = PocketType.caption,
                            color = PocketColors.InkMuted
                        )
                    }
                    Switch(checked = materialYou, onCheckedChange = onMaterialYouChange)
                }
            }
            Text(
                "Material You requires Android 12 or newer. On older devices the Voket palette is used.",
                style = PocketType.caption,
                color = PocketColors.InkFaint,
                modifier = Modifier.padding(horizontal = PocketSpace.xs)
            )
        }
    }
}

/**
 * A miniature, *honest* rendering of one Voket scheme. It draws the real
 * component vocabulary at miniature scale — hero wash, list row, pill button,
 * circular FAB, and a nav bar with a genuinely selected destination — so the
 * preview previews the product rather than some generic rectangles.
 *
 * Also the switcher: tapping a preview applies that theme, so the card is a
 * control and not decoration. Semantics mirror [PocketSegmentedControl] above
 * it, so the two read as one control to a screen reader rather than as a
 * decorative image sitting under a real one.
 */
@Composable
private fun ThemePreviewCard(
    title: String,
    scheme: androidx.compose.material3.ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .pressScale(0.97f)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                // Merged so the card announces as one control. Left unmerged, TalkBack reads the
                // contentDescription here and then the title Text below it again.
                role = Role.RadioButton
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
                contentDescription = "Use the $title theme"
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(PocketShape.lg)
                .background(scheme.background)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) scheme.primary else scheme.outlineVariant,
                    shape = PocketShape.lg
                )
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                /* Mini top bar: wordmark bars + a circular profile action. */
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier
                                .size(width = 44.dp, height = 6.dp)
                                .clip(CircleShape)
                                .background(scheme.onSurface.copy(alpha = 0.82f))
                        )
                        Box(
                            Modifier
                                .size(width = 28.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(scheme.onSurfaceVariant.copy(alpha = 0.6f))
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                    )
                }

                /* Mini hero surface: a wash carrying a real title, plus a pill
                   primary action — the focal-surface idea at 1/4 scale. */
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(PocketShape.md)
                        .background(scheme.primaryContainer)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        Modifier
                            .size(width = 22.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                    )
                    Text(
                        "Inbox",
                        style = PocketType.titleSmall.copy(fontSize = 11.sp),
                        color = scheme.onPrimaryContainer,
                        maxLines = 1
                    )
                    Box(
                        Modifier
                            .size(width = 62.dp, height = 3.dp)
                            .clip(CircleShape)
                            .background(scheme.onPrimaryContainer.copy(alpha = 0.45f))
                    )
                    Spacer(Modifier.height(1.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(scheme.primary)
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Open",
                            style = PocketType.caption.copy(fontSize = 9.sp),
                            color = scheme.onPrimary,
                            maxLines = 1
                        )
                    }
                }

                /* Mini list row: organic avatar, text block, two circular
                   actions — the recurring content shape. */
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(PocketShape.blob)
                            .background(scheme.tertiaryContainer)
                    )
                    Spacer(Modifier.width(7.dp))
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(scheme.onSurface.copy(alpha = 0.62f))
                        )
                        Box(
                            Modifier
                                .size(width = 38.dp, height = 3.dp)
                                .clip(CircleShape)
                                .background(scheme.onSurfaceVariant.copy(alpha = 0.5f))
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainerHighest)
                    )
                    /* Circular FAB, at rest. */
                    Spacer(Modifier.width(5.dp))
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                    )
                }

                /* Mini nav bar with one genuinely selected destination. */
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerLow)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    repeat(4) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == 0) scheme.primaryContainer else scheme.surfaceContainerLow
                                )
                        )
                    }
                }
            }

            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Text(
            title,
            style = PocketType.caption,
            color = if (selected) PocketColors.Accent else PocketColors.InkMuted,
            modifier = Modifier.padding(top = PocketSpace.xs, start = PocketSpace.xs)
        )
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Privacy & Security
   ═══════════════════════════════════════════════════════════════════════════ */

@Composable
fun PrivacySecurityScreen(onBack: () -> Unit, onOpenHidden: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("pocket-proj-security", Context.MODE_PRIVATE) }
    val pinController = remember { PinEntryController(preferences, context) { onOpenHidden() } }
    var useBiometrics by rememberSaveable { mutableStateOf(preferences.getBoolean("biometrics", true)) }
    var timeout by rememberSaveable { mutableStateOf(preferences.getString("lock-timeout", "1 minute") ?: "1 minute") }
    var timeoutMenu by remember { mutableStateOf(false) }
    var blurDemo by rememberSaveable { mutableStateOf(true) }
    val biometricAvailable = remember(context) {
        (context as? FragmentActivity)?.let {
            BiometricManager.from(it).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        } ?: false
    }

    LaunchedEffect(biometricAvailable) {
        if (!biometricAvailable && useBiometrics) {
            useBiometrics = false
            preferences.edit().putBoolean("biometrics", false).apply()
        }
    }

    val creating = pinController.protectedPin == null && pinController.legacyPinHash == null

    PocketScreen(blobs = listOf(PocketColors.BlobLilac, PocketColors.BlobRose, PocketColors.BlobAmber)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PocketSpace.lg, vertical = PocketSpace.sm),
            verticalArrangement = Arrangement.spacedBy(PocketSpace.md)
        ) {
            PocketPageHeader("Privacy & Security", onBack, eyebrow = "Security")

            PocketGroup(shape = PocketShape.xxl) {
                Column(
                    Modifier.padding(
                        horizontal = PocketSpace.lg,
                        vertical = PocketSpace.xl
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PocketSpace.md)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm)
                    ) {
                        PocketIconBadge(shape = PocketShape.blob, size = 36.dp) {
                            PocketLockGlyph(tint = PocketColors.Accent)
                        }
                        Text(
                            when {
                                creating && pinController.setupStep == 2 -> "Confirm your PIN"
                                creating -> "Secure your locker"
                                else -> "Enter your PIN"
                            },
                            style = PocketType.title,
                            color = PocketColors.Ink
                        )
                    }
                    if (creating) {
                        Text(
                            if (pinController.setupStep == 1)
                                "Create a 4-digit PIN to protect your private links."
                            else
                                "Step 2 of 2 · Enter the same PIN again.",
                            style = PocketType.caption,
                            color = PocketColors.InkMuted
                        )
                    }

                    PinDots(
                        entered = pinController.pin.length,
                        verified = pinController.pinVerified
                    )

                    PinKeypad(
                        onDigit = { pinController.acceptDigit(it) },
                        onBiometric = { pinController.onBiometricClick() },
                        onDelete = { pinController.onDeleteClick() }
                    )

                    if (System.currentTimeMillis() < pinController.lockedUntil) {
                        PocketBanner(message = "PIN temporarily locked", icon = Icons.Filled.Warning)
                    }
                    pinController.pinMessage?.let {
                        PocketBanner(
                            message = it,
                            icon = if (pinController.pinVerified) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            accent = if (pinController.pinVerified) PocketColors.Sage else PocketColors.Danger,
                            container = if (pinController.pinVerified) PocketColors.SageSoft else PocketColors.DangerSoft,
                            onAccent = if (pinController.pinVerified) PocketColors.OnSageSoft else PocketColors.OnDangerSoft
                        )
                    }
                }
            }

            PocketSectionLabel("Unlock method")
            PocketGroup(shape = PocketShape.xl) {
                Row(
                    Modifier.padding(horizontal = PocketSpace.md, vertical = PocketSpace.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PocketIconBadge(
                        icon = Icons.Filled.Face,
                        accent = if (biometricAvailable) PocketColors.Accent else PocketColors.InkFaint
                    )
                    Spacer(Modifier.width(PocketSpace.md))
                    Column(Modifier.weight(1f)) {
                        Text("Use biometrics", style = PocketType.bodyStrong, color = PocketColors.Ink)
                        Text(
                            if (biometricAvailable) "Fingerprint or face unlocks hidden content"
                            else "No supported biometric is enrolled on this device",
                            style = PocketType.caption,
                            color = PocketColors.InkMuted
                        )
                    }
                    Switch(
                        checked = useBiometrics && biometricAvailable,
                        enabled = biometricAvailable,
                        onCheckedChange = {
                            useBiometrics = it
                            preferences.edit().putBoolean("biometrics", it).apply()
                            if (!it) pinController.pinMessage = "Biometric unlock disabled"
                        }
                    )
                }
            }

            PocketSectionLabel("Lock timeout")
            PocketGroup(shape = PocketShape.xl) {
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressScale(0.99f)
                            .clickable { timeoutMenu = true }
                            .padding(horizontal = PocketSpace.md, vertical = PocketSpace.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            timeout,
                            style = PocketType.bodyStrong,
                            color = PocketColors.Ink,
                            modifier = Modifier.weight(1f)
                        )
                        PocketCaret()
                    }
                    DropdownMenu(expanded = timeoutMenu, onDismissRequest = { timeoutMenu = false }) {
                        listOf("Immediately", "30 seconds", "1 minute", "5 minutes", "15 minutes").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, style = PocketType.body) },
                                onClick = {
                                    timeout = option
                                    timeoutMenu = false
                                    preferences.edit().putString("lock-timeout", option).apply()
                                }
                            )
                        }
                    }
                }
            }

            PocketSectionLabel("Hidden content")
            PocketCardSurface(shape = PocketShape.xl) {
                Column(Modifier.padding(PocketSpace.md), verticalArrangement = Arrangement.spacedBy(PocketSpace.md)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(PocketShape.lg)
                            .background(PocketColors.SurfaceLow)
                            .pressScale(0.99f)
                            .clickable { blurDemo = !blurDemo }
                            .padding(PocketSpace.md)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                if (blurDemo) "••••••  ••••••" else "Private Notes  ·  6 links",
                                style = PocketType.bodyStrong,
                                color = if (blurDemo) PocketColors.InkFaint else PocketColors.Ink
                            )
                            Text(
                                if (blurDemo) "Private titles stay concealed" else "Private titles are legible",
                                style = PocketType.caption,
                                color = PocketColors.InkMuted
                            )
                        }
                    }
                    PocketHairline()
                    Box(Modifier.fillMaxWidth().pressScale(0.97f)) {
                        Surface(
                            onClick = {
                                if (pinController.pinVerified) {
                                    pinController.startSession()
                                    onOpenHidden()
                                } else {
                                    pinController.pinMessage = "Enter or create your PIN to open hidden content"
                                }
                            },
                            shape = PocketShape.pill,
                            color = PocketColors.Accent,
                            contentColor = PocketColors.OnAccent,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Row(
                                Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PocketLockGlyph(tint = PocketColors.OnAccent)
                                Spacer(Modifier.width(PocketSpace.sm))
                                Text("Open hidden content", style = PocketType.button)
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ── Shared PIN chrome ────────────────────────────────────────────────────── */

@Composable
private fun PinDots(entered: Int, verified: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(4) { index ->
            val filled = index < entered || (verified && index == 0)
            val tone by animateColorAsState(
                if (filled) PocketColors.Accent else PocketColors.SurfaceHighest,
                animationSpec = pocketTween(PocketMotion.micro),
                label = "pinDot$index"
            )
            Box(
                Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(tone)
            )
        }
    }
}

@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onBiometric: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "fingerprint", "0", "delete")
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PocketSpace.sm)
    ) {
        keys.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(PocketSpace.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { value ->
                    val label = when (value) {
                        "fingerprint" -> "Use biometric unlock"
                        "delete" -> "Delete last digit"
                        else -> value
                    }
                    Box(Modifier.weight(1f).pressScale(0.9f)) {
                        Surface(
                            onClick = {
                                when (value) {
                                    "fingerprint" -> onBiometric()
                                    "delete" -> onDelete()
                                    else -> onDigit(value)
                                }
                            },
                            shape = PocketShape.md,
                            color = PocketColors.KeypadFace,
                            contentColor = PocketColors.KeypadGlyph,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.semantics { contentDescription = label }
                            ) {
                                when (value) {
                                    "fingerprint" -> PocketFingerprintGlyph(tint = PocketColors.KeypadGlyph)
                                    "delete" -> PocketBackspaceGlyph(tint = PocketColors.KeypadGlyph)
                                    else -> Text(
                                        value,
                                        style = PocketType.titleSmall.copy(fontSize = 19.sp),
                                        color = PocketColors.KeypadGlyph
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Hidden locker
   ═══════════════════════════════════════════════════════════════════════════ */

@Composable
fun HiddenContentScreen(
    repository: LinkRepository,
    securityPreferences: android.content.SharedPreferences,
    collectionNameById: Map<Long, String>,
    onBack: () -> Unit,
    onOpenLink: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onUnhide: (Long) -> Unit
) {
    val context = LocalContext.current
    val allLinks by repository.observeLinks().collectAsState(initial = emptyList())
    val hiddenLinks = remember(allLinks) { allLinks.filter { it.isHidden } }
    val scope = rememberCoroutineScope()
    var revealed by rememberSaveable { mutableStateOf(false) }
    var showCreateTag by rememberSaveable { mutableStateOf(false) }
    var newHiddenTag by rememberSaveable { mutableStateOf("") }
    var tagError by rememberSaveable { mutableStateOf<String?>(null) }
    var hiddenTags by remember {
        mutableStateOf(
            securityPreferences.getStringSet("hidden-tags", emptySet())?.toSet().orEmpty()
        )
    }
    val customHiddenTags = hiddenTags.sortedBy { it.lowercase() }

    fun persistHiddenTags(updated: Set<String>) {
        val normalized = updated.map(String::trim).filter(String::isNotBlank).toSet()
        hiddenTags = normalized
        securityPreferences.edit().putStringSet("hidden-tags", normalized).apply()
        scope.launch { repository.syncHiddenTags(normalized) }
    }

    fun openCreateTag() {
        newHiddenTag = ""
        tagError = null
        showCreateTag = true
    }

    PocketScreen(blobs = listOf(PocketColors.BlobRose, PocketColors.BlobLilac, PocketColors.BlobMint)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = PocketSpace.lg, vertical = PocketSpace.sm),
            verticalArrangement = Arrangement.spacedBy(PocketSpace.sm)
        ) {
            PocketPageHeader("Hidden Locker", onBack, eyebrow = "Vault") {
                PocketTextAction(if (revealed) "Conceal" else "Reveal") { revealed = !revealed }
            }

            PocketCardSurface(shape = PocketShape.xl) {
                // Surface lays its content out in a Box, which stacks every child at the same
                // corner instead of flowing them down the card. Emitting the header row, the chip
                // row and a spacer as siblings therefore drew them all on top of each other, which
                // is how the #corn pill ended up sitting on the lock badge. One Column, one flow.
                Column(
                    Modifier.padding(PocketSpace.md),
                    verticalArrangement = Arrangement.spacedBy(PocketSpace.md)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PocketIconBadge(shape = PocketShape.blob, size = 42.dp) {
                            PocketLockGlyph(tint = PocketColors.Accent)
                        }
                        Spacer(Modifier.width(PocketSpace.sm))
                        Column(Modifier.weight(1f)) {
                            Text("Hidden tags", style = PocketType.titleSmall, color = PocketColors.Ink)
                            Text(
                                if (customHiddenTags.isEmpty()) "No tags yet · matching tags stay hidden"
                                else customHiddenTags.size.toString() + " private tag" + if (customHiddenTags.size == 1) "" else "s",
                                style = PocketType.caption,
                                color = PocketColors.InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        PocketIconButton(
                            icon = Icons.Filled.Add,
                            contentDescription = "Create hidden tag",
                            onClick = { openCreateTag() }
                        )
                    }
                    if (customHiddenTags.isNotEmpty()) {
                        // The list is a layer under the header rather than part of it, so a pill is
                        // never mistaken for header content and the two cannot grow into each other.
                        PocketHairline()
                        // Horizontally scrollable, so adding tags scrolls instead of overflowing.
                        PocketChipRow {
                            customHiddenTags.forEach { tag ->
                                HiddenTagPill(
                                    tag = tag,
                                    onRemove = { persistHiddenTags(hiddenTags - tag) }
                                )
                            }
                        }
                    }
                }
            }

            PocketSectionHeader(
                title = hiddenLinks.size.toString() + " hidden " +
                    if (hiddenLinks.size == 1) "item" else "items",
                subtitle = if (revealed) "Locker contents are visible" else "Titles and domains stay concealed"
            )

            if (hiddenLinks.isEmpty()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = PocketSpace.xl),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(PocketSpace.md),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PocketIconBadge(shape = CircleShape, size = 112.dp) {
                            PocketLockGlyph(tint = PocketColors.Accent)
                        }
                        Text(
                            "Your locker is empty",
                            style = PocketType.headlineSmall,
                            color = PocketColors.Ink
                        )
                        Text(
                            "Create a hidden tag, then add it to a link to keep that link out of the main library.",
                            style = PocketType.bodySmall,
                            color = PocketColors.InkMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.86f)
                        )
                        PocketTonalButton(
                            text = "Create hidden tag",
                            onClick = { openCreateTag() },
                            icon = Icons.Filled.Add
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    // xs (6dp) was *less* than the 10dp this list spaces its own cards by, so the
                    // "N hidden items" heading sat closer to the first card than the cards sit to
                    // each other and read as part of the list. md matches the library and search
                    // headings. Only the non-empty branch gets this list, and the empty state is a
                    // separate centred Box, so nothing else moves.
                    contentPadding = PaddingValues(top = PocketSpace.md, bottom = PocketSpace.xl),
                    verticalArrangement = Arrangement.spacedBy(PocketSpace.sm)
                ) {
                    items(hiddenLinks, key = { it.id }) { link ->
                        LibraryLinkCard(
                            link = link,
                            collectionName = collectionNameById[link.collectionId] ?: "Inbox",
                            onClick = { onOpenLink(link.id) },
                            onOpenInBrowser = { openInBrowser(context, link.url) },
                            onToggleFavorite = { onToggleFavorite(link.id) },
                            revealContent = revealed,
                            onUnhide = { onUnhide(link.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateTag) {
        PocketDialog(
            onDismissRequest = { showCreateTag = false },
            title = "Create hidden tag",
            body = "Any link carrying this tag is hidden automatically.",
            icon = Icons.Filled.Lock,
            confirmLabel = "Create tag",
            onConfirm = {
                val candidate = newHiddenTag.trim()
                when {
                    candidate.isBlank() -> tagError = "Enter a tag name"
                    candidate.length > 40 -> tagError = "Tag name is too long"
                    candidate.contains(",") -> tagError = "Use one tag at a time"
                    hiddenTags.any { it.equals(candidate, ignoreCase = true) } ->
                        tagError = "That hidden tag already exists"
                    else -> {
                        persistHiddenTags(hiddenTags + candidate)
                        showCreateTag = false
                        newHiddenTag = ""
                    }
                }
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(PocketSpace.xs)) {
                    PocketFormField(
                        value = newHiddenTag,
                        onValueChange = {
                            newHiddenTag = it
                            tagError = null
                        },
                        placeholder = "e.g. private, personal, archive"
                    )
                    tagError?.let {
                        Text(it, style = PocketType.caption, color = PocketColors.Danger)
                    }
                }
            }
        )
    }
}

/** A private tag the user can remove. Only the × acts. */
@Composable
private fun HiddenTagPill(tag: String, onRemove: () -> Unit) {
    Surface(
        shape = PocketShape.pill,
        color = PocketColors.AccentSoft,
        contentColor = PocketColors.OnAccentSoft
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = PocketSpace.sm, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            // Bounded and ellipsized like PocketTagPill: the chip row scrolls, so an unbounded
            // label would not break the layout, it would just push every other tag off-screen.
            Text(
                "#$tag",
                style = PocketType.caption.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
            Box(
                Modifier
                    .padding(start = PocketSpace.xs)
                    .size(22.dp)
                    .clip(CircleShape)
                    .pressScale(0.85f)
                    .clickable(onClick = onRemove)
                    .semantics { contentDescription = "Remove $tag" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Vault unlock
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * PIN entry dialog used to unlock the hidden vault from the home page
 * long-press gesture. Mirrors PrivacySecurityScreen's PIN semantics:
 * create-on-first-use, verify, 5-attempt lockout, expiring session.
 */
@Composable
fun PinGateDialog(
    preferences: android.content.SharedPreferences,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
    title: String = "Hidden locker",
    startSessionOnVerify: Boolean = true
) {
    val context = LocalContext.current
    val pinController = remember {
        PinEntryController(preferences, context, startSessionOnVerify) { onUnlocked() }
    }
    val creating = pinController.protectedPin == null && pinController.legacyPinHash == null

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Surface(
            shape = PocketShape.xxl,
            color = PocketColors.Surface,
            contentColor = PocketColors.Ink,
            shadowElevation = PocketElevation.overlay
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PocketSpace.lg, vertical = PocketSpace.lg),
                verticalArrangement = Arrangement.spacedBy(PocketSpace.md)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PocketIconBadge(shape = PocketShape.blob, size = 44.dp) {
                        PocketLockGlyph(tint = PocketColors.Accent)
                    }
                    Spacer(Modifier.width(PocketSpace.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                creating && pinController.setupStep == 2 -> "Confirm your PIN"
                                creating && title == "Hidden locker" -> "Secure your locker"
                                else -> title
                            },
                            style = PocketType.title,
                            color = PocketColors.Ink
                        )
                        Text(
                            when {
                                creating && pinController.setupStep == 1 ->
                                    "Create a 4-digit PIN to protect your private links."
                                creating && pinController.setupStep == 2 ->
                                    "Step 2 of 2 · Enter the same PIN again."
                                title != "Hidden locker" -> "Enter your PIN to continue"
                                else -> "Enter your PIN to unlock your private links."
                            },
                            style = PocketType.caption,
                            color = PocketColors.InkMuted
                        )
                    }
                    Box(Modifier.pressScale(0.9f)) {
                        Surface(
                            onClick = onDismiss,
                            shape = PocketShape.squircle,
                            color = PocketColors.SurfaceRaised,
                            contentColor = PocketColors.Ink,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Cancel",
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                if (creating) {
                    Text(
                        if (pinController.setupStep == 1) "STEP 1 OF 2" else "STEP 2 OF 2",
                        style = PocketType.overline,
                        color = PocketColors.InkMuted,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                PinDots(
                    entered = pinController.pin.length,
                    verified = pinController.pinVerified,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                if (System.currentTimeMillis() < pinController.lockedUntil) {
                    PocketBanner(message = "PIN temporarily locked", icon = Icons.Filled.Warning)
                }
                pinController.pinMessage?.let {
                    PocketBanner(
                        message = it,
                        icon = if (pinController.pinVerified) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        accent = if (pinController.pinVerified) PocketColors.Sage else PocketColors.Danger,
                        container = if (pinController.pinVerified) PocketColors.SageSoft else PocketColors.DangerSoft,
                        onAccent = if (pinController.pinVerified) PocketColors.OnSageSoft else PocketColors.OnDangerSoft
                    )
                }

                PinKeypad(
                    onDigit = { pinController.acceptDigit(it) },
                    onBiometric = { pinController.onBiometricClick() },
                    onDelete = { pinController.onDeleteClick() }
                )
            }
        }
    }
}

private const val SECURITY_KEY_ALIAS = "pocket-proj-pin-verifier-v1"

private fun securityKey(): java.security.Key {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    keyStore.getKey(SECURITY_KEY_ALIAS, null)?.let { return it }
    val generator = javax.crypto.KeyGenerator.getInstance(
        android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
        "AndroidKeyStore"
    )
    generator.init(
        android.security.keystore.KeyGenParameterSpec.Builder(
            SECURITY_KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
    )
    return generator.generateKey()
}

private fun protectPinVerifier(pin: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, securityKey())
    return Base64.encodeToString(cipher.iv + cipher.doFinal(digest), Base64.NO_WRAP)
}

private fun verifyProtectedPin(pin: String, protected: String): Boolean = runCatching {
    val payload = Base64.decode(protected, Base64.NO_WRAP)
    require(payload.size > 12)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
        Cipher.DECRYPT_MODE,
        securityKey(),
        GCMParameterSpec(128, payload.copyOfRange(0, 12))
    )
    val expected = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
    expected.contentEquals(cipher.doFinal(payload.copyOfRange(12, payload.size)))
}.getOrDefault(false)

private fun newSalt(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

private fun hashPin(pin: String, salt: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

/**
 * The links an export is allowed to contain. Hidden links stay out unless the user explicitly asked
 * to include them and cleared the PIN gate, so the default is the safe one (REQUIREMENTS §8).
 */
internal fun exportableLinks(links: List<LinkEntity>, includeHidden: Boolean): List<LinkEntity> =
    if (includeHidden) links else links.filterNot { it.isHidden }

private fun List<LinkEntity>.toJson(collectionNames: Map<Long, String>): String {
    val root = JSONObject().apply {
        put("schemaVersion", 1)
        put(
            "collections",
            JSONArray().apply {
                collectionNames.values.distinct().forEach { name -> put(JSONObject().put("name", name)) }
            }
        )
    }
    val links = JSONArray()
    this@toJson.forEach { link ->
        links.put(JSONObject().apply {
            put("url", link.url)
            put("title", link.title)
            put("description", link.description)
            put("imageUrl", link.imageUrl)
            put("domain", link.domain)
            put("tags", link.tags)
            put("createdAt", link.createdAt)
            put("isFavorite", link.isFavorite)
            put("isHidden", link.isHidden)
            put("collectionId", link.collectionId)
            put("collection", collectionNames[link.collectionId].orEmpty())
        })
    }
    root.put("links", links)
    return root.toString(2)
}

private fun List<LinkEntity>.toCsv(collectionNames: Map<Long, String>): String = buildString {
    appendLine("url,title,description,imageUrl,domain,tags,createdAt,isFavorite,isHidden,collectionId,collection")
    this@toCsv.forEach { link ->
        val values = listOf(
            link.url, link.title, link.description, link.imageUrl, link.domain, link.tags,
            link.createdAt.toString(), link.isFavorite.toString(), link.isHidden.toString(),
            link.collectionId.toString(), collectionNames[link.collectionId].orEmpty()
        )
        appendLine(values.joinToString(",") { csvEscape(it) })
    }
}

private fun csvEscape(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value

/**
 * Parses a restored JSON backup. Supports the current versioned object format
 * (schemaVersion 1) as well as legacy raw-array exports from earlier builds.
 * Collections are resolved by portable name on import (findings #6/#16).
 */
private fun parseJson(text: String): List<ImportLink> {
    val trimmed = text.trimStart()
    val items = if (trimmed.startsWith("[")) {
        JSONArray(trimmed)
    } else {
        JSONObject(trimmed).optJSONArray("links") ?: JSONArray()
    }
    return List(items.length()) { index ->
        val item = items.getJSONObject(index)
        ImportLink(
            url = item.optString("url"),
            title = item.optString("title"),
            description = item.optString("description"),
            imageUrl = item.optString("imageUrl"),
            domain = item.optString("domain"),
            tags = item.optString("tags"),
            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
            isFavorite = item.optBoolean("isFavorite", false),
            collectionId = item.optLong("collectionId", 0L),
            collectionName = item.optString("collection").trim().takeIf { it.isNotEmpty() },
            isHidden = item.optBoolean("isHidden", false)
        )
    }.filter { it.url.isNotBlank() }
}

/**
 * Writes [text] to [uri], reporting whether the bytes actually landed.
 *
 * The SAF picker can hand back a URI whose stream cannot be opened at all, and a provider can fail
 * part-way through a write. Neither is worth crashing over, but the caller must not claim the export
 * succeeded when it did not — a user who is told a backup exists will not think to check.
 */
private fun writeText(context: Context, uri: Uri, text: String): Boolean = runCatching {
    val stream = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
    stream.bufferedWriter().use { it.write(text) }
    true
}.getOrDefault(false)

private fun readText(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
