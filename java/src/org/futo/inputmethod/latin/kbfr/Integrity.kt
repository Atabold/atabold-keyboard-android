package org.futo.inputmethod.latin.kbfr

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import org.futo.inputmethod.latin.BuildConfig

enum class IntegrityProblem { WRONG_SIGNATURE, DEBUGGABLE, KEY_NOT_IN_HARDWARE }

data class IntegrityStatus(val problems: List<IntegrityProblem>, val keyStorage: KeyStorage?) {
    val ok: Boolean get() = problems.isEmpty()
}

// kbfr fork: tamper checks run at keyboard start and before online actions (spec 8). Any problem
// turns online features off and shows the red banner. Creating the device key on first use also
// happens here.
object Integrity {
    // Blocking (Keystore, maybe key generation): call it off the main thread.
    fun check(context: Context): IntegrityStatus {
        val problems = mutableListOf<IntegrityProblem>()
        if (signingCertSha256(context) != BuildConfig.KBFR_EXPECTED_CERT_SHA256) problems += IntegrityProblem.WRONG_SIGNATURE
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) problems += IntegrityProblem.DEBUGGABLE

        val storage = try {
            DeviceKey.ensureExists()
            DeviceKey.storage()
        } catch (_: Exception) {
            null
        }
        if (storage == null || storage == KeyStorage.SOFTWARE) problems += IntegrityProblem.KEY_NOT_IN_HARDWARE

        return IntegrityStatus(problems, storage)
    }

    private fun signingCertSha256(context: Context): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        // Exactly one signer is expected; anything else is not our release build.
        return signatures?.singleOrNull()?.let { RequestSigning.sha256Hex(it.toByteArray()) }
    }
}
