package app.parivaar.panchang

import android.Manifest
import android.app.LocaleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.locale.LocaleApplier
import app.parivaar.panchang.shell.DebugGate
import app.parivaar.panchang.shell.GateUi
import app.parivaar.panchang.shell.KeyLostGate
import app.parivaar.panchang.shell.KeyUnavailableGate
import app.parivaar.panchang.shell.LoadingGate
import app.parivaar.panchang.shell.ShellScreen
import app.parivaar.panchang.shell.ShellViewModel
import app.parivaar.panchang.feature.onboarding.LanguageOptions
import app.parivaar.panchang.feature.onboarding.OnboardingHost
import app.parivaar.panchang.feature.onboarding.OnboardingPlatform
import app.parivaar.panchang.feature.settings.LanguageChoice
import app.parivaar.panchang.feature.settings.PrivacySheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: ShellViewModel by viewModels()

    private var pendingLocation: ((Boolean) -> Unit)? = null
    private var pendingNotifications: ((Boolean) -> Unit)? = null

    private val locationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingLocation?.invoke(granted)
            pendingLocation = null
        }
    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingNotifications?.invoke(granted)
            pendingNotifications = null
        }

    private val platform =
        object : OnboardingPlatform {
            override fun hasCoarseLocation() = granted(Manifest.permission.ACCESS_COARSE_LOCATION)

            override fun requestCoarseLocation(onResult: (granted: Boolean) -> Unit) {
                pendingLocation = onResult
                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }

            override fun needsNotificationPermission() =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !granted(Manifest.permission.POST_NOTIFICATIONS)

            override fun requestNotifications(onResult: (granted: Boolean) -> Unit) {
                pendingNotifications = onResult
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            override fun applyLocale(tag: String) = LocaleApplier.apply(this@MainActivity, tag)
        }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LocaleApplier.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_PanchangApp) // leave the launch (splash) theme
        super.onCreate(savedInstanceState)
        val clear = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(clear, clear),
            navigationBarStyle = SystemBarStyle.auto(clear, clear),
        )
        setContent { App() }
    }

    @Composable
    private fun App() {
        val settings by vm.settings.collectAsStateWithLifecycle()
        val realUi by vm.gate.ui.collectAsStateWithLifecycle()
        val forced by DebugGate.override.collectAsStateWithLifecycle()
        val ui = forced ?: realUi
        val busy by vm.gate.busy.collectAsStateWithLifecycle()
        val homeName by vm.homePlaceName.collectAsStateWithLifecycle()

        // Settings language changes (and onboarding step 1) reach the platform locale here.
        LaunchedEffect(settings.localeTag) {
            val tag = settings.localeTag
            if (tag != null && tag != appliedTag()) LocaleApplier.apply(this@MainActivity, tag)
        }

        PanchangTheme(settings.themeMode, settings.dynamicColor, settings.badaText, settings.reduceMotion) {
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    when (ui) {
                        GateUi.Loading -> LoadingGate()
                        // A debug-forced gate must never reach the real recovery actions (no wipe).
                        GateUi.KeyLost ->
                            KeyLostGate(busy, if (forced == null) vm.gate::startFreshConfirmed else ({}), ::finish)
                        GateUi.KeyUnavailable ->
                            KeyUnavailableGate(
                                busy,
                                if (forced == null) vm.gate::retry else ({}),
                                if (forced == null) vm.gate::startFreshConfirmed else ({}),
                            )
                        GateUi.Onboarding -> OnboardingFlow()
                        GateUi.Shell -> {
                            val languages =
                                LanguageOptions.all.map { LanguageChoice(it.tag, stringResource(it.nameRes)) }
                            ShellScreen(settings, homeName, languages, vm.conventions, vm.appearance)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun OnboardingFlow() {
        val step by vm.onboarding.state.collectAsStateWithLifecycle()
        var privacy by rememberSaveable { mutableStateOf(false) }
        PredictiveBackHandler(enabled = step.step.ordinal > 0 && !privacy) { progress ->
            try {
                progress.collect { }
                vm.onboarding.back()
            } catch (_: CancellationException) {
                // gesture cancelled
            }
        }
        OnboardingHost(
            machine = vm.onboarding,
            platform = platform,
            onOpenPrivacy = { privacy = true },
            onDone = { },
        )
        if (privacy) PrivacySheet(onDismiss = { privacy = false })
    }

    /** The locale the platform currently applies for this app, or null when none is set. */
    private fun appliedTag(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().ifEmpty { null }
        } else {
            LocaleApplier.storedTag(this)
        }
}
