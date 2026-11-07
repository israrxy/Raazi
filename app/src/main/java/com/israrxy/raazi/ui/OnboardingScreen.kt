package com.israrxy.raazi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.data.region.RegionManager
import com.israrxy.raazi.model.MusicItem
import com.israrxy.raazi.ui.onboarding.OnboardingAiKeyPage
import com.israrxy.raazi.ui.onboarding.OnboardingAudioLyricsPage
import com.israrxy.raazi.ui.onboarding.OnboardingHeroPage
import com.israrxy.raazi.ui.onboarding.OnboardingPersonalizePage
import com.israrxy.raazi.ui.onboarding.OnboardingSoundDataPage
import com.israrxy.raazi.ui.onboarding.OnboardingTastePickerPage
import com.israrxy.raazi.ui.settings.SettingsRegionDialog
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import com.israrxy.raazi.viewmodel.OnboardingArtistSeed
import com.israrxy.raazi.viewmodel.OnboardingTasteCatalog
import com.israrxy.raazi.viewmodel.commitOnboardingTasteSelections
import com.israrxy.raazi.viewmodel.loadOnboardingTasteSuggestions
import com.israrxy.raazi.viewmodel.searchOnboardingTaste
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOTAL_ONBOARDING_PAGES = 8
private const val AI_KEY_PAGE = 4
private const val TASTE_PICKER_PAGE = 5
private const val PRIVACY_PAGE = 6
private const val ACCOUNT_PAGE = 7

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: MusicPlayerViewModel,
    onFinish: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { TOTAL_ONBOARDING_PAGES })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }

    // Settings & State flows
    val privacyAccepted by settingsDataStore.privacyPolicyAccepted.collectAsState(initial = false)
    val themeMode by settingsDataStore.themeMode.collectAsState(initial = "System")
    val useDynamicColor by settingsDataStore.useDynamicColor.collectAsState(initial = true)
    val pastelAccent by settingsDataStore.pastelAccent.collectAsState(initial = "Emerald")
    val pureBlack by settingsDataStore.pureBlack.collectAsState(initial = false)
    val blurPlayerBackground by settingsDataStore.blurPlayerBackground.collectAsState(initial = true)

    val preferredLyricsType by settingsDataStore.preferredLyricsType.collectAsState(initial = "ROMANIZED")
    val defaultLyricsView by settingsDataStore.defaultLyricsView.collectAsState(initial = "NORMAL")
    val defaultPlayerScreen by settingsDataStore.defaultPlayerScreen.collectAsState(initial = "LAST_USED")
    val microPlayerEnabled by settingsDataStore.microPlayerEnabled.collectAsState(initial = true)

    val audioQuality by settingsDataStore.audioQuality.collectAsState(initial = "High")
    val flacEnabled by settingsDataStore.flacEnabled.collectAsState(initial = false)
    val dataSaverEnabled by settingsDataStore.dataSaverEnabled.collectAsState(initial = false)
    val downloadWifiOnly by settingsDataStore.downloadWifiOnly.collectAsState(initial = false)
    val equalizerPreset by settingsDataStore.equalizerPreset.collectAsState(initial = "Normal")
    val bassBoostStrength by settingsDataStore.bassBoostStrength.collectAsState(initial = 0.toShort())

    val contentCountry by settingsDataStore.contentCountry.collectAsState(initial = "AUTO")
    val detectedCountryCode = remember { RegionManager.detectHardwareCountry(context) }

    val resolvedCountryCode = remember(contentCountry, detectedCountryCode) {
        if (contentCountry.isBlank() || contentCountry.equals("AUTO", ignoreCase = true)) {
            detectedCountryCode
        } else {
            contentCountry
        }
    }

    // Taste Picker State
    var defaultArtists by remember {
        mutableStateOf(OnboardingTasteCatalog.defaultArtistsForRegion(resolvedCountryCode))
    }
    var defaultSongs by remember {
        mutableStateOf(OnboardingTasteCatalog.defaultSongsForRegion(resolvedCountryCode))
    }
    var displayedArtists by remember { mutableStateOf(defaultArtists) }
    var displayedSongs by remember { mutableStateOf(defaultSongs) }
    var tasteSearchQuery by remember { mutableStateOf("") }
    var isTasteSearching by remember { mutableStateOf(false) }

    val selectedArtists = remember { mutableStateMapOf<String, OnboardingArtistSeed>() }
    val selectedSongs = remember { mutableStateMapOf<String, MusicItem>() }

    // Load regional trending songs & artists when region changes
    LaunchedEffect(resolvedCountryCode) {
        val (loadedArtists, loadedSongs) = viewModel.loadOnboardingTasteSuggestions(resolvedCountryCode)
        defaultArtists = loadedArtists
        defaultSongs = loadedSongs
        if (tasteSearchQuery.isBlank()) {
            displayedArtists = loadedArtists
            displayedSongs = loadedSongs
        }
    }

    // Debounced live search for songs and artists
    LaunchedEffect(tasteSearchQuery, defaultArtists, defaultSongs) {
        val query = tasteSearchQuery.trim()
        if (query.isBlank()) {
            isTasteSearching = false
            displayedArtists = defaultArtists
            displayedSongs = defaultSongs
            return@LaunchedEffect
        }
        isTasteSearching = true
        delay(320)
        val (foundArtists, foundSongs) = viewModel.searchOnboardingTaste(query)
        displayedArtists = if (foundArtists.isNotEmpty()) foundArtists else defaultArtists
        displayedSongs = if (foundSongs.isNotEmpty()) foundSongs else defaultSongs
        isTasteSearching = false
    }

    var showRegionDialog by remember { mutableStateOf(false) }
    var isWebViewVisible by remember { mutableStateOf(false) }

    fun finishOnboarding() {
        viewModel.commitOnboardingTasteSelections(
            selectedArtists = selectedArtists.values.toList(),
            selectedSongs = selectedSongs.values.toList()
        )
        scope.launch {
            settingsDataStore.setPrivacyPolicyAccepted(true)
            settingsDataStore.setOnboardingCompleted(true)
            onFinish()
        }
    }

    // Disable swiping past Privacy page until accepted
    val isSwipeAllowed = pagerState.currentPage != ACCOUNT_PAGE &&
        (pagerState.currentPage != PRIVACY_PAGE || privacyAccepted)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ambient background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        // Horizontal Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = isSwipeAllowed
        ) { page ->
            when (page) {
                0 -> OnboardingHeroPage(
                    contentCountry = contentCountry,
                    detectedCountryCode = detectedCountryCode,
                    onRegionClick = { showRegionDialog = true }
                )
                1 -> OnboardingPersonalizePage(
                    themeMode = themeMode,
                    useDynamicColor = useDynamicColor,
                    pastelAccent = pastelAccent,
                    pureBlack = pureBlack,
                    blurPlayerBackground = blurPlayerBackground,
                    contentCountry = contentCountry,
                    detectedCountryCode = detectedCountryCode,
                    onThemeChange = { mode -> scope.launch { settingsDataStore.setThemeMode(mode) } },
                    onDynamicColorToggle = { enabled -> scope.launch { settingsDataStore.setDynamicColor(enabled) } },
                    onPastelAccentChange = { accent -> scope.launch { settingsDataStore.setPastelAccent(accent) } },
                    onPureBlackToggle = { enabled -> scope.launch { settingsDataStore.setPureBlack(enabled) } },
                    onBlurPlayerBackgroundToggle = { enabled ->
                        scope.launch { settingsDataStore.setBlurPlayerBackground(enabled) }
                    },
                    onRegionClick = { showRegionDialog = true }
                )
                2 -> OnboardingAudioLyricsPage(
                    preferredLyricsType = preferredLyricsType,
                    audioQuality = audioQuality,
                    microPlayerEnabled = microPlayerEnabled,
                    defaultLyricsView = defaultLyricsView,
                    defaultPlayerScreen = defaultPlayerScreen,
                    onLyricsTypeChange = { type -> scope.launch { settingsDataStore.setPreferredLyricsType(type) } },
                    onAudioQualityChange = { quality -> scope.launch { settingsDataStore.setAudioQuality(quality) } },
                    onMicroPlayerToggle = { enabled -> scope.launch { settingsDataStore.setMicroPlayerEnabled(enabled) } },
                    onDefaultLyricsViewChange = { view ->
                        scope.launch { settingsDataStore.setDefaultLyricsView(view) }
                    },
                    onDefaultPlayerScreenChange = { screen ->
                        scope.launch { settingsDataStore.setDefaultPlayerScreen(screen) }
                    }
                )
                3 -> OnboardingSoundDataPage(
                    audioQuality = audioQuality,
                    flacEnabled = flacEnabled,
                    dataSaverEnabled = dataSaverEnabled,
                    downloadWifiOnly = downloadWifiOnly,
                    equalizerPreset = equalizerPreset,
                    onSelectSoundQualityMode = { mode ->
                        scope.launch {
                            when (mode) {
                                "FLAC" -> {
                                    settingsDataStore.setDataSaver(false)
                                    settingsDataStore.setFlacEnabled(true)
                                    settingsDataStore.setAudioQuality("Very High")
                                }
                                "SAVER" -> {
                                    settingsDataStore.setFlacEnabled(false)
                                    settingsDataStore.setDataSaver(true)
                                    settingsDataStore.setAudioQuality("Low")
                                }
                                else -> {
                                    settingsDataStore.setFlacEnabled(false)
                                    settingsDataStore.setDataSaver(false)
                                    settingsDataStore.setAudioQuality("High")
                                }
                            }
                        }
                    },
                    onSelectEqPreset = { preset, bass ->
                        scope.launch {
                            settingsDataStore.setEqualizerPreset(preset)
                            settingsDataStore.setBassBoostStrength(bass)
                        }
                    },
                    onDownloadWifiOnlyToggle = { enabled ->
                        scope.launch { settingsDataStore.setDownloadWifiOnly(enabled) }
                    }
                )
                AI_KEY_PAGE -> OnboardingAiKeyPage(
                    onContinue = {
                        scope.launch { pagerState.animateScrollToPage(TASTE_PICKER_PAGE) }
                    },
                    onSkip = {
                        scope.launch { pagerState.animateScrollToPage(TASTE_PICKER_PAGE) }
                    }
                )
                TASTE_PICKER_PAGE -> OnboardingTastePickerPage(
                    artists = displayedArtists,
                    songs = displayedSongs,
                    selectedArtistIds = selectedArtists.keys,
                    selectedSongIds = selectedSongs.keys,
                    searchQuery = tasteSearchQuery,
                    isSearching = isTasteSearching,
                    onSearchQueryChange = { tasteSearchQuery = it },
                    onClearSearch = { tasteSearchQuery = "" },
                    onToggleArtist = { artist ->
                        if (selectedArtists.containsKey(artist.id)) {
                            selectedArtists.remove(artist.id)
                        } else {
                            selectedArtists[artist.id] = artist
                        }
                    },
                    onToggleSong = { song ->
                        if (selectedSongs.containsKey(song.id)) {
                            selectedSongs.remove(song.id)
                        } else {
                            selectedSongs[song.id] = song
                        }
                    },
                    onSkipStep = {
                        scope.launch { pagerState.animateScrollToPage(PRIVACY_PAGE) }
                    }
                )
                PRIVACY_PAGE -> OnboardingPrivacyPage(
                    accepted = privacyAccepted,
                    onAcceptToggle = { accepted ->
                        scope.launch { settingsDataStore.setPrivacyPolicyAccepted(accepted) }
                    }
                )
                ACCOUNT_PAGE -> OnboardingAccountPage(
                    viewModel = viewModel,
                    settingsDataStore = settingsDataStore,
                    onWebViewVisibilityChange = { isWebViewVisible = it },
                    onFinished = {
                        scope.launch {
                            delay(250)
                            finishOnboarding()
                        }
                    }
                )
            }
        }

        // Top Skip button
        if (pagerState.currentPage < ACCOUNT_PAGE && !isWebViewVisible) {
            TextButton(
                onClick = {
                    scope.launch {
                        if (privacyAccepted) {
                            finishOnboarding()
                        } else {
                            pagerState.animateScrollToPage(PRIVACY_PAGE)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Skip",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Bottom controller & Page Indicators
        if (!isWebViewVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Back", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(TOTAL_ONBOARDING_PAGES) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 20.dp else 6.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "dot_width"
                        )
                        val color by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                            },
                            label = "dot_color"
                        )

                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                // Next / Finish Button
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    val canProceed = pagerState.currentPage != PRIVACY_PAGE || privacyAccepted
                    Button(
                        onClick = {
                            if (pagerState.currentPage < ACCOUNT_PAGE) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                finishOnboarding()
                            }
                        },
                        enabled = canProceed,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        val isLastPage = pagerState.currentPage == ACCOUNT_PAGE
                        Text(
                            text = if (isLastPage) "Start" else "Next",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isLastPage) {
                                Icons.Default.PlayArrow
                            } else {
                                Icons.AutoMirrored.Filled.ArrowForward
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Region Selection Dialog
        if (showRegionDialog) {
            SettingsRegionDialog(
                currentCountryCode = contentCountry,
                detectedCountryCode = detectedCountryCode,
                onDismiss = { showRegionDialog = false },
                onRegionSelected = { selectedCode ->
                    scope.launch {
                        settingsDataStore.setContentCountry(selectedCode)
                        val resolved = if (selectedCode.isBlank() || selectedCode.equals("AUTO", ignoreCase = true)) {
                            RegionManager.detectHardwareCountry(context)
                        } else {
                            selectedCode
                        }
                        RegionManager.applyLocale(
                            resolved,
                            RegionManager.getLanguageCode()
                        )
                    }
                    showRegionDialog = false
                }
            )
        }
    }
}
