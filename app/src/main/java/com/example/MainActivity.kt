package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.configureAppCheck
import com.example.data.ai.ClipperAiRepository
import com.example.data.repository.ClipProjectRepository
import com.example.data.video.AndroidMediaVideoProcessor
import com.example.data.youtube.YouTubeMetadataRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.components.ClipperTopBar
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.ClipperTheme
import com.example.ui.viewmodel.ClipperViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class ClipperTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
    STUDIO(R.string.nav_studio, Icons.Rounded.MovieCreation, Icons.Outlined.MovieCreation, "nav_studio"),
    PROJECTS(R.string.nav_projects, Icons.Rounded.Folder, Icons.Outlined.Folder, "nav_projects")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            ClipperTheme {
                ClipperAppRoot()
            }
        }
    }
}

@Composable
private fun ClipperAppRoot() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            ClipperAuthenticatedShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName,
                userEmail = user.email
            )
        }
    }
}

@Composable
private fun ClipperAuthenticatedShell(
    currentUserId: String,
    userDisplayName: String?,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: ClipperViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ClipperViewModel(
                    projectRepository = ClipProjectRepository(db),
                    youtubeRepository = YouTubeMetadataRepository(),
                    videoProcessor = AndroidMediaVideoProcessor(app),
                    aiRepository = ClipperAiRepository(),
                    currentUserId = currentUserId,
                    cacheDir = app.cacheDir
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    var activeTab by rememberSaveable { mutableStateOf(ClipperTab.HOME) }

    BackHandler(enabled = activeTab != ClipperTab.HOME) {
        activeTab = ClipperTab.HOME
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                ClipperTopBar(
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    onSignOutClick = {
                        signOutUser(credentialManager = credentialManager, scope = scope)
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        ClipperTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (activeTab == tab) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        ClipperTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (activeTab == tab) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (activeTab) {
                        ClipperTab.HOME -> {
                            HomeScreen(
                                uiState = uiState,
                                projectsState = projectsState,
                                formatDuration = viewModel::formatDuration,
                                onUrlChanged = viewModel::onUrlChanged,
                                onPasteUrl = viewModel::onPasteUrl,
                                onClearUrl = viewModel::onClearUrl,
                                onAspectRatioSelected = viewModel::onSelectAspectRatio,
                                onFindClipsClick = viewModel::findClipsFromYouTubeUrl,
                                onLocalVideoPicked = viewModel::onLocalVideoSelected,
                                onOpenInStudio = { project ->
                                    viewModel.openProject(project)
                                    activeTab = ClipperTab.STUDIO
                                },
                                onDeleteProject = viewModel::deleteProject
                            )
                        }
                        ClipperTab.STUDIO -> {
                            StudioScreen(
                                uiState = uiState,
                                formatDuration = viewModel::formatDuration,
                                onSelectClip = viewModel::selectClip,
                                onExportLocalClip = viewModel::exportLocalClip,
                                onStrategyPromptChanged = viewModel::onStrategyPromptChanged,
                                onGenerateStrategy = viewModel::generateDeepStrategy,
                                onVoiceStarted = viewModel::onVoiceRecordingStarted,
                                onVoiceStopped = viewModel::onVoiceRecordingStopped,
                                onVoiceError = viewModel::onVoiceError,
                                onGoHome = { activeTab = ClipperTab.HOME }
                            )
                        }
                        ClipperTab.PROJECTS -> {
                            ProjectsScreen(
                                projectsState = projectsState,
                                formatDuration = viewModel::formatDuration,
                                onOpenProject = { project ->
                                    viewModel.openProject(project)
                                    activeTab = ClipperTab.STUDIO
                                },
                                onDeleteProject = viewModel::deleteProject
                            )
                        }
                    }
                }
            }
        }
    }
}
