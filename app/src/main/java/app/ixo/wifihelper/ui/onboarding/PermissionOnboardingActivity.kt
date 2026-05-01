package app.ixo.wifihelper.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.ixo.wifihelper.R
import app.ixo.wifihelper.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * 權限引導 Activity，在首次啟動或權限缺失時引導使用者授予所有必要權限。
 *
 * 權限請求順序（依 Android 版本要求）：
 * 1. ACCESS_FINE_LOCATION — WiFi 掃描需要精確位置權限
 * 2. ACCESS_BACKGROUND_LOCATION (API 29+) — 背景 WiFi 監控需要背景位置權限
 *    CRITICAL: 必須在前景位置權限授予後才能單獨請求
 * 3. NEARBY_WIFI_DEVICES (API 33+) — WiFi 裝置探索權限
 * 4. POST_NOTIFICATIONS (API 33+, optional) — 通知權限
 *
 * 完成後導航至 [MainActivity]，並在 SharedPreferences 中記錄引導完成狀態。
 */
@AndroidEntryPoint
class PermissionOnboardingActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "permission_onboarding"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }

    // UI elements
    private lateinit var appIcon: ImageView
    private lateinit var titleText: TextView
    private lateinit var stepDescription: TextView
    private lateinit var grantButton: Button
    private lateinit var skipButton: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var stepIndicator: TextView

    /** Current step in the permission flow */
    private var currentStep = 0

    /** All permission steps for the current API level */
    private val permissionSteps: List<PermissionStep> by lazy { buildPermissionSteps() }

    // ── Permission launchers ─────────────────────────────────────────────

    /**
     * Launcher for single runtime permission requests (Steps 1, 3, 4).
     */
    private val singlePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            advanceToNextStep()
        } else {
            handlePermissionDenied()
        }
    }

    /**
     * Launcher for opening app settings (used for background location on API 30+
     * and for retry after permanent denial).
     */
    private val appSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check if the permission was granted in settings
        val step = permissionSteps.getOrNull(currentStep) ?: return@registerForActivityResult
        if (isPermissionGranted(step.permission)) {
            advanceToNextStep()
        } else {
            // User returned without granting — show explanation again
            updateUiForCurrentStep()
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fast path: if all permissions are already granted, skip onboarding
        if (allRequiredPermissionsGranted()) {
            markOnboardingComplete()
            navigateToMain()
            return
        }

        setContentView(R.layout.activity_permission_onboarding)
        bindViews()

        // Find the first step that still needs permission
        currentStep = findFirstUngrantedStep()
        updateUiForCurrentStep()

        grantButton.setOnClickListener { requestCurrentStepPermission() }
        skipButton.setOnClickListener { handleSkip() }
    }

    // ── View binding ─────────────────────────────────────────────────────

    private fun bindViews() {
        appIcon = findViewById(R.id.onboarding_app_icon)
        titleText = findViewById(R.id.onboarding_title)
        stepDescription = findViewById(R.id.onboarding_step_description)
        grantButton = findViewById(R.id.onboarding_grant_button)
        skipButton = findViewById(R.id.onboarding_skip_button)
        progressBar = findViewById(R.id.onboarding_progress)
        stepIndicator = findViewById(R.id.onboarding_step_indicator)
    }

    // ── Permission step definitions ──────────────────────────────────────

    private fun buildPermissionSteps(): List<PermissionStep> {
        val steps = mutableListOf<PermissionStep>()

        // Step 1: Fine location (all API levels)
        steps.add(
            PermissionStep(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                description = getString(R.string.onboarding_location_description),
                isOptional = false,
                requestMode = RequestMode.RUNTIME
            )
        )

        // Step 2: Background location (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            steps.add(
                PermissionStep(
                    permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    description = getString(R.string.onboarding_background_location_description),
                    isOptional = false,
                    // API 29: can request via runtime dialog
                    // API 30+: must open app settings (system requirement)
                    requestMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        RequestMode.APP_SETTINGS
                    } else {
                        RequestMode.RUNTIME
                    }
                )
            )
        }

        // Step 3: Nearby WiFi devices (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            steps.add(
                PermissionStep(
                    permission = Manifest.permission.NEARBY_WIFI_DEVICES,
                    description = getString(R.string.onboarding_nearby_devices_description),
                    isOptional = false,
                    requestMode = RequestMode.RUNTIME
                )
            )
        }

        // Step 4: Notifications (API 33+, optional)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            steps.add(
                PermissionStep(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    description = getString(R.string.onboarding_notifications_description),
                    isOptional = true,
                    requestMode = RequestMode.RUNTIME
                )
            )
        }

        return steps
    }

    // ── UI updates ───────────────────────────────────────────────────────

    private fun updateUiForCurrentStep() {
        val step = permissionSteps.getOrNull(currentStep)
        if (step == null) {
            // All steps done
            finishOnboarding()
            return
        }

        stepDescription.text = step.description

        // Show/hide skip button (only for optional permissions)
        skipButton.visibility = if (step.isOptional) View.VISIBLE else View.GONE

        // Update progress
        progressBar.max = permissionSteps.size
        progressBar.progress = currentStep + 1
        stepIndicator.text = getString(
            R.string.onboarding_step_indicator,
            currentStep + 1,
            permissionSteps.size
        )

        // Update button text based on request mode
        grantButton.text = if (step.requestMode == RequestMode.APP_SETTINGS) {
            getString(R.string.onboarding_open_settings)
        } else {
            getString(R.string.onboarding_grant_permission)
        }
    }

    // ── Permission request logic ─────────────────────────────────────────

    private fun requestCurrentStepPermission() {
        val step = permissionSteps.getOrNull(currentStep) ?: return

        // Already granted? Skip ahead.
        if (isPermissionGranted(step.permission)) {
            advanceToNextStep()
            return
        }

        when (step.requestMode) {
            RequestMode.RUNTIME -> {
                singlePermissionLauncher.launch(step.permission)
            }
            RequestMode.APP_SETTINGS -> {
                // Open app settings for background location (API 30+)
                openAppSettings()
            }
        }
    }

    private fun handlePermissionDenied() {
        val step = permissionSteps.getOrNull(currentStep) ?: return

        if (step.isOptional) {
            // Optional permission denied — just skip
            advanceToNextStep()
            return
        }

        // Check if we should show rationale or direct to settings
        if (!shouldShowRequestPermissionRationale(step.permission)) {
            // User selected "Don't ask again" — must go to app settings
            stepDescription.text = getString(R.string.onboarding_permission_denied_settings)
            grantButton.text = getString(R.string.onboarding_open_settings)
            grantButton.setOnClickListener {
                openAppSettings()
                // Reset the click listener for next time
                grantButton.setOnClickListener { requestCurrentStepPermission() }
            }
        } else {
            // Can still ask — update description with explanation
            stepDescription.text = getString(R.string.onboarding_permission_denied_retry, step.description)
        }
    }

    private fun handleSkip() {
        val step = permissionSteps.getOrNull(currentStep) ?: return
        if (step.isOptional) {
            advanceToNextStep()
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────

    private fun advanceToNextStep() {
        currentStep = findFirstUngrantedStep(startFrom = currentStep + 1)
        if (currentStep >= permissionSteps.size) {
            finishOnboarding()
        } else {
            updateUiForCurrentStep()
        }
    }

    private fun finishOnboarding() {
        markOnboardingComplete()
        navigateToMain()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun allRequiredPermissionsGranted(): Boolean {
        return buildPermissionSteps().filter { !it.isOptional }.all { isPermissionGranted(it.permission) }
    }

    /**
     * Find the first step (starting from [startFrom]) whose permission is not yet granted.
     * For optional steps that are not granted, they are still included so the user gets a chance.
     */
    private fun findFirstUngrantedStep(startFrom: Int = 0): Int {
        for (i in startFrom until permissionSteps.size) {
            if (!isPermissionGranted(permissionSteps[i].permission)) {
                return i
            }
        }
        return permissionSteps.size // All granted
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        appSettingsLauncher.launch(intent)
    }

    private fun markOnboardingComplete() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()
    }

    // ── Data classes ─────────────────────────────────────────────────────

    private data class PermissionStep(
        val permission: String,
        val description: String,
        val isOptional: Boolean,
        val requestMode: RequestMode
    )

    private enum class RequestMode {
        /** Request via standard runtime permission dialog */
        RUNTIME,
        /** Must open app settings (e.g., background location on API 30+) */
        APP_SETTINGS
    }
}
