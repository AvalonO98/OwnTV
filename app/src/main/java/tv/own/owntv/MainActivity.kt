package tv.own.owntv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.features.profiles.ProfileGate
import tv.own.owntv.features.profiles.ProfilesViewModel
import tv.own.owntv.features.setup.Onboarding
import tv.own.owntv.features.shell.OwnTVShell
import tv.own.owntv.features.shell.ShellViewModel
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.UiZoom

class MainActivity : ComponentActivity() {
    private val player: tv.own.owntv.player.OwnTVPlayer by inject()

    override fun onStop() {
        super.onStop()
        // Backgrounded (Home / another app): stop playback and free the demuxer cache + decoder
        // buffers — holding them while invisible got the process LMK-killed on real TVs.
        if (!isChangingConfigurations) player.onAppBackgrounded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: ShellViewModel = koinViewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val accent by viewModel.accent.collectAsStateWithLifecycle()
            val customAccent by viewModel.customAccent.collectAsStateWithLifecycle()
            val uiZoomPercent by viewModel.uiZoomPercent.collectAsStateWithLifecycle()
            val avatarId by viewModel.avatarId.collectAsStateWithLifecycle()
            val profileName by viewModel.profileName.collectAsStateWithLifecycle()
            val sourceSummary by viewModel.sourceSummary.collectAsStateWithLifecycle()
            val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
            val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
            val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

            val profilesVm: ProfilesViewModel = koinViewModel()
            val profiles by profilesVm.profiles.collectAsStateWithLifecycle()
            var gatePassed by remember { mutableStateOf(false) }
            var addingProfile by remember { mutableStateOf(false) }
            // A backup restore deletes-then-reinserts profiles, so the list is briefly EMPTY while
            // the shell is showing — without this, the shell unmounts and remounts, dumping the user
            // out of Settings → Backup & Restore mid-restore. Only the cold start waits for the load.
            var everHadProfiles by remember { mutableStateOf(false) }
            LaunchedEffect(profiles) { if (profiles.isNotEmpty()) everHadProfiles = true }

            // "Refresh on startup" — re-sync sources once the active profile is known.
            LaunchedEffect(activeProfileId) {
                if ((activeProfileId ?: -1L) >= 0L) viewModel.refreshOnStartIfEnabled()
            }

            OwnTVTheme(themeMode = themeMode, accent = accent, systemInDarkTheme = isSystemInDarkTheme(), customAccent = customAccent) {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density * UiZoom.factor(uiZoomPercent), base.fontScale),
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(OwnTVTheme.colors.background)) {
                        val profile = activeProfileId
                        when {
                            profile == null -> Unit // loading
                            // Adding a profile from the gate → onboard the new profile.
                            addingProfile -> Onboarding(
                                firstRun = false,
                                onDone = { addingProfile = false; gatePassed = true },
                                onCancel = { addingProfile = false },
                                modifier = Modifier.fillMaxSize(),
                            )
                            // First run (no profile yet) → full onboarding.
                            profile < 0L -> Onboarding(
                                firstRun = true,
                                onDone = { gatePassed = true },
                                onCancel = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Profiles still loading (≥0 means at least one exists) — avoid a gate/shell flicker.
                            profiles.isEmpty() && !everHadProfiles -> Unit
                            // Run 2+: "Who's watching?" — choose a profile or add one.
                            !gatePassed -> ProfileGate(
                                onEnter = { gatePassed = true },
                                onAddProfile = { addingProfile = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                            else -> OwnTVShell(
                                selectedSection = selectedSection,
                                onSelectSection = viewModel::selectSection,
                                themeMode = themeMode,
                                uiZoomPercent = uiZoomPercent,
                                onSetZoom = viewModel::setUiZoom,
                                avatarId = avatarId,
                                onSetAvatar = viewModel::setAvatar,
                                profileName = profileName,
                                sourceSummary = sourceSummary,
                                isOffline = !isOnline,
                                onExitApp = { finish() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}
