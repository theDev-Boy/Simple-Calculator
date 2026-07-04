package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Folder
import com.example.data.MediaItem
import com.example.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class PasscodeSetupState {
    NEEDS_SETUP,
    CONFIRMING,
    SET_UP
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaultRepository(application)
    private val context = application.applicationContext

    // --- State Observables ---
    val isUnlocked = MutableStateFlow(false)
    val passcodeSetupState = MutableStateFlow(PasscodeSetupState.NEEDS_SETUP)
    val calculatorExpression = MutableStateFlow("")
    val calculatorResult = MutableStateFlow("")
    val displayMessage = MutableStateFlow("Set Your Secret Passcode") // For passcode feedback
    
    // UI feedback state
    val showShakeTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showToast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val undoItem = MutableStateFlow<MediaItem?>(null)

    // Dark Mode (Default ON as requested: "DEFAULT: ON (dark mode)")
    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Export progress
    val exportProgress = MutableStateFlow<Float?>(null)
    val exportSummary = MutableStateFlow<String?>(null)

    // Current Folder Scope (Null for "All Photos"/"All Videos")
    val activePhotoFolder = MutableStateFlow<Folder?>(null)
    val activeVideoFolder = MutableStateFlow<Folder?>(null)

    // Loaded Folders & Items
    val photoFolders = repository.getFoldersByType("photo")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoFolders = repository.getFoldersByType("video")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePhotos = repository.getActiveMediaByType("photo")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeVideos = repository.getActiveMediaByType("video")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedItems = repository.getTrashedItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Passcode temporary during confirmation
    private var tempPasscode = ""

    init {
        // Evaluate passcode state on launch
        val isSet = repository.isPasscodeSet()
        passcodeSetupState.value = if (isSet) PasscodeSetupState.SET_UP else PasscodeSetupState.NEEDS_SETUP
        displayMessage.value = if (isSet) "Simple Calculator" else "Set Your Secret Passcode"

        // Auto clean expired trash items (> 30 days) on app start
        viewModelScope.launch {
            repository.cleanExpiredTrash()
        }

        // Load Dark Mode pref from shared pref
        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        _isDarkMode.value = prefs.getBoolean("dark_mode_enabled", true)
    }

    // --- Theme Control ---
    fun toggleDarkMode() {
        val nextVal = !_isDarkMode.value
        _isDarkMode.value = nextVal
        context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("dark_mode_enabled", nextVal)
            .apply()
    }

    // --- Sound & Haptic Support ---
    fun performHapticFeedback() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(35)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    // --- Calculator & Authentication Logic ---
    fun onCalculatorButtonPress(char: String) {
        performHapticFeedback()
        val currentExpr = calculatorExpression.value

        when (char) {
            "AC" -> {
                calculatorExpression.value = ""
                calculatorResult.value = ""
                if (passcodeSetupState.value == PasscodeSetupState.NEEDS_SETUP) {
                    displayMessage.value = "Set Your Secret Passcode"
                } else if (passcodeSetupState.value == PasscodeSetupState.CONFIRMING) {
                    displayMessage.value = "Confirm Your Passcode"
                } else {
                    displayMessage.value = "Simple Calculator"
                }
            }
            "C" -> {
                if (currentExpr.isNotEmpty()) {
                    calculatorExpression.value = currentExpr.dropLast(1)
                }
            }
            "=" -> {
                handleCalculatorEquals()
            }
            "+", "-", "×", "÷" -> {
                // Ensure we don't start with operator or double-operators
                if (currentExpr.isNotEmpty() && !isOperator(currentExpr.last())) {
                    calculatorExpression.value = currentExpr + char
                } else if (currentExpr.isEmpty() && char == "-") {
                    calculatorExpression.value = char
                }
            }
            else -> {
                // Numeric or decimal characters
                calculatorExpression.value = currentExpr + char
            }
        }
    }

    private fun isOperator(c: Char): Boolean = c == '+' || c == '-' || c == '×' || c == '÷'

    private fun handleCalculatorEquals() {
        val currentExpr = calculatorExpression.value
        if (currentExpr.isEmpty()) return

        when (passcodeSetupState.value) {
            PasscodeSetupState.NEEDS_SETUP -> {
                // User enters passcode for the first time
                if (currentExpr.length >= 4 && currentExpr.all { it.isDigit() }) {
                    tempPasscode = currentExpr
                    passcodeSetupState.value = PasscodeSetupState.CONFIRMING
                    calculatorExpression.value = ""
                    calculatorResult.value = ""
                    displayMessage.value = "Confirm Your Passcode"
                } else {
                    triggerShakeAndAlert("Passcode must be at least 4 digits")
                }
            }
            PasscodeSetupState.CONFIRMING -> {
                // User confirms the passcode
                if (currentExpr == tempPasscode) {
                    val saved = repository.savePasscode(tempPasscode)
                    if (saved) {
                        passcodeSetupState.value = PasscodeSetupState.SET_UP
                        isUnlocked.value = true
                        calculatorExpression.value = ""
                        calculatorResult.value = ""
                        displayMessage.value = "Simple Calculator"
                        showToast.tryEmit("Passcode Set Successfully!")
                    } else {
                        triggerShakeAndAlert("Error saving passcode. Try again.")
                        resetSetupFlow()
                    }
                } else {
                    triggerShakeAndAlert("Passcodes mismatch! Retrying...")
                    resetSetupFlow()
                }
            }
            PasscodeSetupState.SET_UP -> {
                // Check if passcode entered matches correct passcode
                val isCorrect = repository.verifyPasscode(currentExpr)
                if (isCorrect) {
                    // Unlock and open vault
                    isUnlocked.value = true
                    calculatorExpression.value = ""
                    calculatorResult.value = ""
                    displayMessage.value = "Simple Calculator"
                    showToast.tryEmit("Vault Unlocked")
                } else {
                    // Evaluate math expression normally
                    val result = evaluateMathExpression(currentExpr)
                    calculatorResult.value = result
                }
            }
        }
    }

    private fun resetSetupFlow() {
        tempPasscode = ""
        passcodeSetupState.value = PasscodeSetupState.NEEDS_SETUP
        calculatorExpression.value = ""
        calculatorResult.value = ""
        displayMessage.value = "Set Your Secret Passcode"
    }

    private fun triggerShakeAndAlert(msg: String) {
        showShakeTrigger.tryEmit(Unit)
        showToast.tryEmit(msg)
        performHapticFeedback()
    }

    // --- Math Evaluator ---
    private fun evaluateMathExpression(expr: String): String {
        val cleanExpr = expr.replace("×", "*").replace("÷", "/")
        if (cleanExpr.isEmpty()) return ""
        return try {
            val result = ExpressionParser(cleanExpr).parse()
            if (result % 1.0 == 0.0) {
                result.toLong().toString()
            } else {
                val formatted = String.format("%.8f", result).trimEnd('0').trimEnd('.')
                if (formatted == "-0") "0" else formatted
            }
        } catch (e: Exception) {
            "Error"
        }
    }

    // --- Passcode Change Settings ---
    fun changePasscode(oldPasscode: String, newPasscode: String): Boolean {
        if (repository.verifyPasscode(oldPasscode)) {
            val saved = repository.savePasscode(newPasscode)
            if (saved) {
                showToast.tryEmit("Passcode Updated Successfully!")
                return true
            }
        } else {
            showToast.tryEmit("Incorrect Current Passcode")
        }
        return false
    }

    fun resetPasscodeFromSettings(currentPasscode: String): Boolean {
        if (repository.verifyPasscode(currentPasscode)) {
            repository.resetPasscode()
            passcodeSetupState.value = PasscodeSetupState.NEEDS_SETUP
            isUnlocked.value = false
            displayMessage.value = "Set Your Secret Passcode"
            showToast.tryEmit("Passcode Reset. Set a new one.")
            return true
        } else {
            showToast.tryEmit("Incorrect Current Passcode")
            return false
        }
    }

    // --- Folder Control ---
    fun createFolder(name: String, type: String) {
        viewModelScope.launch {
            if (name.trim().isNotEmpty()) {
                repository.insertFolder(Folder(name = name.trim(), type = type))
                showToast.tryEmit("Folder '$name' created")
            }
        }
    }

    fun renameFolder(folder: Folder, newName: String) {
        viewModelScope.launch {
            if (newName.trim().isNotEmpty()) {
                repository.updateFolder(folder.copy(name = newName.trim()))
                showToast.tryEmit("Folder renamed")
            }
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
            showToast.tryEmit("Folder and contents deleted")
        }
    }

    // --- Media Import / Adding ---
    fun importSelectedMedia(uris: List<Uri>, type: String, folderId: Long?) {
        viewModelScope.launch {
            var count = 0
            uris.forEach { uri ->
                val item = repository.importMedia(uri, type, folderId)
                if (item != null) count++
            }
            if (count > 0) {
                showToast.tryEmit("Imported $count files to vault")
            } else {
                showToast.tryEmit("Import failed")
            }
        }
    }

    fun saveCapturedPhoto(tempFile: File, folderId: Long?) {
        viewModelScope.launch {
            val item = repository.saveCameraPhoto(tempFile, folderId)
            if (item != null) {
                showToast.tryEmit("Photo captured and stored securely")
            } else {
                showToast.tryEmit("Failed to save captured photo")
            }
        }
    }

    // --- Media Operations ---
    fun trashMediaItem(mediaItem: MediaItem) {
        viewModelScope.launch {
            val updated = mediaItem.copy(
                isInTrash = true,
                trashExpiryDate = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000) // 30 days
            )
            repository.updateMediaItem(updated)
            undoItem.value = updated
            showToast.tryEmit("Moved to Recently Deleted")
        }
    }

    fun undoTrash() {
        val item = undoItem.value ?: return
        viewModelScope.launch {
            repository.updateMediaItem(item.copy(isInTrash = false, trashExpiryDate = null))
            undoItem.value = null
            showToast.tryEmit("Restored item")
        }
    }

    fun restoreMediaItem(mediaItem: MediaItem) {
        viewModelScope.launch {
            repository.updateMediaItem(mediaItem.copy(isInTrash = false, trashExpiryDate = null))
            showToast.tryEmit("Item restored")
        }
    }

    fun deleteMediaPermanently(mediaItem: MediaItem) {
        viewModelScope.launch {
            repository.deleteMediaItem(mediaItem)
            showToast.tryEmit("Item deleted permanently")
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
            showToast.tryEmit("Trash cleared")
        }
    }

    fun unhideMediaItem(mediaItem: MediaItem) {
        viewModelScope.launch {
            val success = repository.unhideMediaItem(mediaItem)
            if (success) {
                showToast.tryEmit("Restored to public gallery")
            } else {
                showToast.tryEmit("Error unhiding item")
            }
        }
    }

    // Move to folder operation
    fun moveMediaToFolder(mediaItem: MediaItem, folderId: Long?) {
        viewModelScope.launch {
            repository.updateMediaItem(mediaItem.copy(folderId = folderId))
            showToast.tryEmit("Item moved successfully")
        }
    }

    // --- Share Utility ---
    fun shareMediaItem(mediaItem: MediaItem, onShareIntent: (Uri) -> Unit) {
        viewModelScope.launch {
            val tempFile = repository.createTempShareFile(mediaItem)
            if (tempFile != null) {
                // Get public content provider URI
                val authority = "${context.packageName}.fileprovider"
                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, tempFile)
                onShareIntent(uri)
                
                showToast.tryEmit("Shared. File will be removed from public access shortly.")
                
                // Auto delete after 5 minutes
                delay(5L * 60 * 1000)
                repository.deleteTempShareFile(tempFile)
            } else {
                showToast.tryEmit("Failed to share file")
            }
        }
    }

    // --- Unhide ALL Content ---
    fun unhideAllContent() {
        viewModelScope.launch {
            exportProgress.value = 0.0f
            var photosRestored = 0
            var videosRestored = 0

            // Get active files
            val photos = repository.getActiveMediaByType("photo").first()
            val videos = repository.getActiveMediaByType("video").first()
            val total = photos.size + videos.size

            if (total == 0) {
                exportProgress.value = null
                exportSummary.value = "No content to restore"
                return@launch
            }

            var processed = 0
            for (p in photos) {
                val success = repository.unhideMediaItem(p)
                if (success) photosRestored++
                processed++
                exportProgress.value = processed.toFloat() / total
            }

            for (v in videos) {
                val success = repository.unhideMediaItem(v)
                if (success) videosRestored++
                processed++
                exportProgress.value = processed.toFloat() / total
            }

            exportProgress.value = null
            exportSummary.value = "$photosRestored photos and $videosRestored videos have been restored"
        }
    }

    fun clearExportSummary() {
        exportSummary.value = null
    }

    // Folder counts
    fun getMediaItemsInFolder(folderId: Long, type: String): Flow<List<MediaItem>> {
        return repository.getMediaByFolder(folderId)
    }

    fun getFolderCount(folderId: Long): Flow<Int> {
        return repository.getMediaCountByFolder(folderId)
    }

    fun getFolderThumbnail(folderId: Long): Flow<MediaItem?> {
        return repository.getFirstItemInFolder(folderId)
    }

    // Lock Vault
    fun lockVault() {
        isUnlocked.value = false
    }
}

// --- Expression Parser Helper Class ---
private class ExpressionParser(private val str: String) {
    private var pos = -1
    private var ch = 0

    private fun nextChar() {
        ch = if (++pos < str.length) str[pos].code else -1
    }

    private fun eat(charToEat: Int): Boolean {
        while (ch == ' '.code) nextChar()
        if (ch == charToEat) {
            nextChar()
            return true
        }
        return false
    }

    fun parse(): Double {
        nextChar()
        val x = parseExpression()
        if (pos < str.length) throw RuntimeException("Unexpected character: " + ch.toChar())
        return x
    }

    private fun parseExpression(): Double {
        var x = parseTerm()
        while (true) {
            if (eat('+'.code)) x += parseTerm() // addition
            else if (eat('-'.code)) x -= parseTerm() // subtraction
            else return x
        }
    }

    private fun parseTerm(): Double {
        var x = parseFactor()
        while (true) {
            if (eat('*'.code)) x *= parseFactor() // multiplication
            else if (eat('/'.code)) {
                val divisor = parseFactor()
                if (divisor == 0.0) throw ArithmeticException("Division by zero")
                x /= divisor // division
            }
            else return x
        }
    }

    private fun parseFactor(): Double {
        if (eat('+'.code)) return parseFactor() // unary plus
        if (eat('-'.code)) return -parseFactor() // unary minus

        var x: Double
        val startPos = pos
        if (eat('('.code)) { // parentheses
            x = parseExpression()
            eat(')'.code)
        } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) { // numbers
            while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
            x = str.substring(startPos, pos).toDouble()
        } else {
            throw RuntimeException("Unexpected token: " + ch.toChar())
        }
        return x
    }
}
