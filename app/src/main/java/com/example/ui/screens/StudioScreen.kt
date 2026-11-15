package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.audio.AudioRecorderHelper
import com.example.domain.model.ClipProject
import com.example.domain.model.ClipSuggestion
import com.example.ui.viewmodel.ClipExportStatus
import com.example.ui.viewmodel.ClipperUiState
import com.example.ui.viewmodel.StrategyStatus
import com.example.ui.viewmodel.VoiceTranscriptionStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    uiState: ClipperUiState,
    formatDuration: (Long) -> String,
    onSelectClip: (String) -> Unit,
    onExportLocalClip: (ClipSuggestion) -> Unit,
    onStrategyPromptChanged: (String) -> Unit,
    onGenerateStrategy: (String?) -> Unit,
    onVoiceStarted: () -> Unit,
    onVoiceStopped: (java.io.File?) -> Unit,
    onVoiceError: (String) -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recorder = remember(context) { AudioRecorderHelper(context) }

    DisposableEffect(Unit) {
        onDispose { recorder.stopAndRelease() }
    }

    val startVoiceCapture = {
        recorder.startRecording().fold(
            onSuccess = { onVoiceStarted() },
            onFailure = { err ->
                onVoiceError(err.localizedMessage ?: "Unable to start microphone recording.")
            }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                startVoiceCapture()
            } else {
                onVoiceError(context.getString(R.string.mic_permission_rationale))
            }
        }
    )

    val project = uiState.activeProject
    if (project == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.widthIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MovieCreation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = stringResource(R.string.studio_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.studio_no_project),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onGoHome,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.testTag("studio_go_home_button")
                    ) {
                        Text("Explore Videos")
                    }
                }
            }
        }
        return
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val isWide = maxWidth >= 860.dp

        LazyColumn(
            modifier = Modifier
                .widthIn(max = if (isWide) 1140.dp else 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (isWide) 24.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StudioHeaderCard(
                    project = project,
                    uiState = uiState,
                    formatDuration = formatDuration
                )
            }

            item {
                Text(
                    text = "AI Clip Candidates (${project.clips.size})",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (project.clips.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No clip segments generated yet for this project.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(project.clips, key = { it.clipId }) { clip ->
                    val isSelected = uiState.selectedClipId == clip.clipId
                    ClipSuggestionCard(
                        clip = clip,
                        isSelected = isSelected,
                        isLocalSource = project.sourceType == ClipProject.SOURCE_LOCAL,
                        exportStatus = uiState.clipExportStatus,
                        formatDuration = formatDuration,
                        onSelect = { onSelectClip(clip.clipId) },
                        onExport = { onExportLocalClip(clip) }
                    )
                }
            }

            item {
                DeepStrategyCard(
                    strategyPrompt = uiState.strategyPrompt,
                    strategyStatus = uiState.strategyStatus,
                    voiceStatus = uiState.voiceStatus,
                    onPromptChanged = onStrategyPromptChanged,
                    onGenerate = { onGenerateStrategy(null) },
                    onQuickChip = { chipPrompt -> onGenerateStrategy(chipPrompt) },
                    onMicClick = {
                        if (uiState.voiceStatus is VoiceTranscriptionStatus.Recording) {
                            val recorded = recorder.stopRecording()
                            onVoiceStopped(recorded)
                        } else {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                startVoiceCapture()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StudioHeaderCard(
    project: ClipProject,
    uiState: ClipperUiState,
    formatDuration: (Long) -> String
) {
    val context = LocalContext.current
    val exportedFile = (uiState.clipExportStatus as? ClipExportStatus.Exported)?.result?.outputFile
    val localUri = uiState.activeLocalVideo?.uri
    val playableUri: Uri? = when {
        exportedFile != null && exportedFile.exists() -> Uri.fromFile(exportedFile)
        localUri != null -> localUri
        else -> null
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (playableUri != null) {
                Text(
                    text = if (exportedFile != null) "Exported Local Short Preview" else "Local Source Preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                            }
                        },
                        update = { view ->
                            if (view.tag != playableUri.toString()) {
                                view.tag = playableUri.toString()
                                view.setVideoURI(playableUri)
                                view.seekTo(100)
                            }
                        },
                        modifier = Modifier
                            .aspectRatio(
                                when (project.aspectRatio) {
                                    ClipProject.ASPECT_9_16 -> 9f / 16f
                                    ClipProject.ASPECT_1_1 -> 1f
                                    else -> 16f / 9f
                                }
                            )
                            .fillMaxSize()
                    )
                }
            } else if (project.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = project.thumbnailUrl,
                    contentDescription = stringResource(R.string.cd_video_thumbnail),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
            }

            uiState.activeLocalVideo?.keyframes?.takeIf { it.isNotEmpty() }?.let { frames ->
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(frames) { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 78.dp, height = 54.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }

            Text(
                text = project.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = project.channelName.ifBlank { "Creator" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (project.durationSeconds > 0L) {
                    Text(
                        text = formatDuration(project.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = project.aspectRatio,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (project.sourceType == ClipProject.SOURCE_YOUTUBE && project.sourceUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(project.sourceUrl))
                        context.startActivity(intent)
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag("open_on_youtube_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Watch Source on YouTube", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClipSuggestionCard(
    clip: ClipSuggestion,
    isSelected: Boolean,
    isLocalSource: Boolean,
    exportStatus: ClipExportStatus,
    formatDuration: (Long) -> String,
    onSelect: () -> Unit,
    onExport: () -> Unit
) {
    val isExportingThis = exportStatus is ClipExportStatus.Exporting && exportStatus.clipId == clip.clipId
    val isExportedThis = exportStatus is ClipExportStatus.Exported && exportStatus.clipId == clip.clipId

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onSelect)
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = clip.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatDuration(clip.startSeconds)} – ${formatDuration(clip.endSeconds)}  (${clip.durationSeconds}s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${clip.hookScore}/100",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (clip.rationale.isNotBlank()) {
                Text(
                    text = clip.rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (clip.hashtags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    clip.hashtags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (isLocalSource) {
                if (isExportingThis) {
                    val progress = (exportStatus as ClipExportStatus.Exporting).progress
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (isExportedThis) {
                    val res = (exportStatus as ClipExportStatus.Exported).result
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Saved locally (${res.sizeBytes / 1024} KB) — preview playing above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = onExport,
                    enabled = !isExportingThis,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag("export_clip_button_${clip.clipId}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_export_local_clip),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeepStrategyCard(
    strategyPrompt: String,
    strategyStatus: StrategyStatus,
    voiceStatus: VoiceTranscriptionStatus,
    onPromptChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onQuickChip: (String) -> Unit,
    onMicClick: () -> Unit
) {
    val isThinkingOrStreaming = strategyStatus is StrategyStatus.Thinking ||
        strategyStatus is StrategyStatus.Streaming
    val isRecording = voiceStatus is VoiceTranscriptionStatus.Recording
    val isTranscribing = voiceStatus is VoiceTranscriptionStatus.Transcribing

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.strategy_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Gemini 4 Carbon · ThinkingLevel.HIGH",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Rewrite first 3-second viral hook",
                    "Design 3-part Shorts series arc",
                    "Retention pacing & caption timing plan"
                ).forEach { chip ->
                    AssistChip(
                        onClick = { onQuickChip(chip) },
                        label = { Text(chip, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = strategyPrompt,
                    onValueChange = onPromptChanged,
                    placeholder = { Text(stringResource(R.string.strategy_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("strategy_prompt_input")
                )

                FilledIconButton(
                    onClick = onMicClick,
                    enabled = !isTranscribing,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isRecording) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (isRecording) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("voice_mic_button")
                ) {
                    if (isTranscribing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = stringResource(R.string.cd_mic_transcribe)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = voiceStatus !is VoiceTranscriptionStatus.Idle) {
                when (voiceStatus) {
                    is VoiceTranscriptionStatus.Recording -> {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.voice_recording_hint),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is VoiceTranscriptionStatus.Transcribing -> {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.voice_transcribing),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is VoiceTranscriptionStatus.Error -> {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = voiceStatus.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    VoiceTranscriptionStatus.Idle -> Unit
                }
            }

            Button(
                onClick = onGenerate,
                enabled = !isThinkingOrStreaming,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("generate_strategy_button")
            ) {
                if (isThinkingOrStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deep Thinking…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_ask_strategy),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            when (strategyStatus) {
                is StrategyStatus.Streaming -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strategyStatus.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                is StrategyStatus.Success -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strategyStatus.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                is StrategyStatus.Error -> {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strategyStatus.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}
