package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.ClipProject
import com.example.domain.video.LocalVideoInfo
import com.example.ui.components.AuthorizedLocalUploadCard
import com.example.ui.components.ProjectCardItem
import com.example.ui.components.YouTubeUrlInputCard
import com.example.ui.viewmodel.AnalysisStatus
import com.example.ui.viewmodel.ClipperUiState
import com.example.ui.viewmodel.ProjectsUiState

@Composable
fun HomeScreen(
    uiState: ClipperUiState,
    projectsState: ProjectsUiState,
    formatDuration: (Long) -> String,
    onUrlChanged: (String) -> Unit,
    onPasteUrl: (String) -> Unit,
    onClearUrl: () -> Unit,
    onAspectRatioSelected: (String) -> Unit,
    onFindClipsClick: () -> Unit,
    onLocalVideoPicked: (Uri) -> Unit,
    onOpenInStudio: (ClipProject) -> Unit,
    onDeleteProject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                onLocalVideoPicked(uri)
            }
        }
    )

    val launchLocalVideoPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    val isBusy = uiState.analysisStatus is AnalysisStatus.FetchingMetadata ||
        uiState.analysisStatus is AnalysisStatus.AnalyzingVideo

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val isWide = maxWidth >= 820.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .widthIn(max = 1140.dp)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    item {
                        YouTubeUrlInputCard(
                            urlInput = uiState.urlInput,
                            urlValidation = uiState.urlValidation,
                            selectedAspectRatio = uiState.selectedAspectRatio,
                            analysisStatus = uiState.analysisStatus,
                            onUrlChanged = onUrlChanged,
                            onPasteUrl = onPasteUrl,
                            onClearUrl = onClearUrl,
                            onAspectRatioSelected = onAspectRatioSelected,
                            onFindClipsClick = onFindClipsClick
                        )
                    }
                    item {
                        AuthorizedLocalUploadCard(
                            isBusy = isBusy,
                            onPickLocalVideoClick = launchLocalVideoPicker
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    item {
                        PreviewSectionCard(
                            uiState = uiState,
                            formatDuration = formatDuration,
                            onOpenInStudio = onOpenInStudio
                        )
                    }
                    item {
                        RecentProjectsSection(
                            projectsState = projectsState,
                            formatDuration = formatDuration,
                            onOpenProject = onOpenInStudio,
                            onDeleteProject = onDeleteProject
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    YouTubeUrlInputCard(
                        urlInput = uiState.urlInput,
                        urlValidation = uiState.urlValidation,
                        selectedAspectRatio = uiState.selectedAspectRatio,
                        analysisStatus = uiState.analysisStatus,
                        onUrlChanged = onUrlChanged,
                        onPasteUrl = onPasteUrl,
                        onClearUrl = onClearUrl,
                        onAspectRatioSelected = onAspectRatioSelected,
                        onFindClipsClick = onFindClipsClick
                    )
                }
                item {
                    AuthorizedLocalUploadCard(
                        isBusy = isBusy,
                        onPickLocalVideoClick = launchLocalVideoPicker
                    )
                }
                item {
                    PreviewSectionCard(
                        uiState = uiState,
                        formatDuration = formatDuration,
                        onOpenInStudio = onOpenInStudio
                    )
                }
                item {
                    RecentProjectsSection(
                        projectsState = projectsState,
                        formatDuration = formatDuration,
                        onOpenProject = onOpenInStudio,
                        onDeleteProject = onDeleteProject
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PreviewSectionCard(
    uiState: ClipperUiState,
    formatDuration: (Long) -> String,
    onOpenInStudio: (ClipProject) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.preview_section_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            AnimatedContent(
                targetState = uiState.analysisStatus,
                label = "preview_status"
            ) { status ->
                when (status) {
                    is AnalysisStatus.FetchingMetadata -> {
                        LoadingStateBox(
                            title = "Retrieving Video Metadata",
                            subtitle = "Fetching public details without downloading streams…"
                        )
                    }
                    is AnalysisStatus.AnalyzingVideo -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            uiState.activeProject?.let { project ->
                                ActiveVideoBanner(
                                    project = project,
                                    localVideo = uiState.activeLocalVideo,
                                    formatDuration = formatDuration
                                )
                            }
                            LoadingStateBox(
                                title = "Gemini 4 Argon Analyzing Moments",
                                subtitle = "Scoring viral hooks and identifying top Shorts timestamps…"
                            )
                        }
                    }
                    is AnalysisStatus.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = status.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            uiState.activeProject?.let { project ->
                                ActiveVideoBanner(
                                    project = project,
                                    localVideo = uiState.activeLocalVideo,
                                    formatDuration = formatDuration
                                )
                            }
                        }
                    }
                    else -> {
                        val project = uiState.activeProject
                        if (project == null) {
                            EmptyPreviewState()
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ActiveVideoBanner(
                                    project = project,
                                    localVideo = uiState.activeLocalVideo,
                                    formatDuration = formatDuration
                                )
                                if (project.clips.isNotEmpty()) {
                                    Text(
                                        text = "Top AI-Discovered Clips (${project.clips.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    project.clips.take(3).forEach { clip ->
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = clip.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${formatDuration(clip.startSeconds)} – ${formatDuration(clip.endSeconds)} (${clip.durationSeconds}s)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = "${clip.hookScore} pts",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Button(
                                    onClick = { onOpenInStudio(project) },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("open_studio_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MovieCreation,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open in Clip Studio", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveVideoBanner(
    project: ClipProject,
    localVideo: LocalVideoInfo?,
    formatDuration: (Long) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (project.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = project.thumbnailUrl,
                contentDescription = stringResource(R.string.cd_video_thumbnail),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
        } else if (localVideo != null && localVideo.keyframes.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(localVideo.keyframes) { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_video_thumbnail),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 110.dp, height = 148.dp)
                            .clip(MaterialTheme.shapes.small)
                    )
                }
            }
        }

        Text(
            text = project.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = project.channelName.ifBlank { "Creator" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (project.durationSeconds > 0L) {
                Text("•", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = formatDuration(project.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (project.sourceType == ClipProject.SOURCE_YOUTUBE) {
                        stringResource(R.string.youtube_compliance_note)
                    } else {
                        stringResource(R.string.local_compliance_note)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun EmptyPreviewState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_studio_empty),
            contentDescription = stringResource(R.string.cd_studio_hero),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(MaterialTheme.shapes.medium)
        )
        Text(
            text = stringResource(R.string.preview_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.preview_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingStateBox(title: String, subtitle: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.5.dp
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecentProjectsSection(
    projectsState: ProjectsUiState,
    formatDuration: (Long) -> String,
    onOpenProject: (ClipProject) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.recent_projects_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        when (projectsState) {
            is ProjectsUiState.Loading -> {
                LoadingStateBox(
                    title = "Syncing Projects",
                    subtitle = "Loading your saved Shorts projects from Cloud Firestore…"
                )
            }
            is ProjectsUiState.Error -> {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = projectsState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            is ProjectsUiState.Success -> {
                if (projectsState.projects.isEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.recent_projects_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        projectsState.projects.take(6).forEach { project ->
                            ProjectCardItem(
                                project = project,
                                formattedDuration = formatDuration(project.durationSeconds),
                                onOpenClick = { onOpenProject(project) },
                                onDeleteClick = { onDeleteProject(project.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
