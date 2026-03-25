package com.aria.picoclaw

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PicoClawManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val binaryName = "picoclaw-arm64"
    private val targetDir = File(context.filesDir, "picoclaw")
    val binaryFile = File(targetDir, binaryName)

    /**
     * Copy PicoClaw binary from assets to internal storage (first run only).
     */
    fun install(): Boolean {
        if (binaryFile.exists()) return true

        return try {
            targetDir.mkdirs()
            context.assets.open(binaryName).use { input ->
                binaryFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            // Set executable permission
            Runtime.getRuntime().exec("chmod +x ${binaryFile.absolutePath}").waitFor()
            true
        } catch (e: Exception) {
            // Binary not bundled in assets — graceful fallback
            false
        }
    }

    fun isInstalled(): Boolean = binaryFile.exists() && binaryFile.canExecute()

    fun getBinaryPath(): String = binaryFile.absolutePath
}
