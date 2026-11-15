package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ClipperAiRepository
import com.example.data.repository.ClipProjectRepository
import com.example.data.youtube.YouTubeMetadataRepository
import com.example.domain.model.ClipProject
import com.example.domain.model.ClipSuggestion
import com.example.domain.video.ClipExportResult
import com.example.domain.video.LocalVideoInfo
import com.example.domain.video.VideoProcessor
import com.example.domain.youtube.YouTubeUrlParser
import com.example.domain.youtube.YouTubeUrlValidationResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface ProjectsUiState {
    data object Loading : ProjectsUiState
    data class Success(val projects: List<ClipProject>) : ProjectsUiState
    data class Error(val message: String) : ProjectsUiState
}

sealed interface AnalysisStatus {
    data object Idle : AnalysisStatus
    data object FetchingMetadata : AnalysisStatus
    data object AnalyzingVideo : AnalysisStatus
    data class Ready(val message: String) : AnalysisStatus
    data class Error(val message: String) : AnalysisStatus
}

sealed interface StrategyStatus {
    data object Idle : StrategyStatus
    data object Thinking : StrategyStatus
    data class Streaming(val text: String) : StrategyStatus
    data class Success(val text: String) : StrategyStatus
    data class Error(val message: String) : StrategyStatus
}

sealed interface VoiceTranscriptionStatus {
    data object Idle : VoiceTranscriptionStatus
    data object Recording : VoiceTranscriptionStatus
    data object Transcribing : VoiceTranscriptionStatus
    data class Error(val message: String) : VoiceTranscriptionStatus
}

sealed interface ClipExportStatus {
    data object Idle : ClipExportStatus
    data class Exporting(val clipId: String, val progress: Float) : ClipExportStatus
    data class Exported(val clipId: String, val result: ClipExportResult) : ClipExportStatus
    data class Error(val message: String) : ClipExportStatus
}

data class ClipperUiState(
    val urlInput: String = "",
    val urlValidation: YouTubeUrlValidationResult = YouTubeUrlValidationResult.Empty,
    val selectedAspectRatio: String = ClipProject.ASPECT_9_16,
    val activeProject: ClipProject? = null,
    val activeLocalVideo: LocalVideoInfo? = null,
    val selectedClipId: String? = null,
    val analysisStatus: AnalysisStatus = AnalysisStatus.Idle,
    val strategyPrompt: String = "",
    val strategyStatus: StrategyStatus = StrategyStatus.Idle,
    val voiceStatus: VoiceTranscriptionStatus = VoiceTranscriptionStatus.Idle,
    val clipExportStatus: ClipExportStatus = ClipExportStatus.Idle,
    val usedDataApiKey: Boolean? = null
)

class ClipperViewModel(
    private val projectRepository: ClipProjectRepository,
    private val youtubeRepository: YouTubeMetadataRepository,
    private val videoProcessor: VideoProcessor,
    private val aiRepository: ClipperAiRepository,
    private val currentUserId: String,
    private val cacheDir: File
) : ViewModel() {

    val projectsState: StateFlow<ProjectsUiState> = projectRepository.observeProjects(currentUserId)
        .map<List<ClipProject>, ProjectsUiState> { ProjectsUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing projects", rootError)
            emit(ProjectsUiState.Error(rootError.message ?: "Failed to load saved projects."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ProjectsUiState.Loading
        )

    private val _uiState = MutableStateFlow(ClipperUiState())
    val uiState: StateFlow<ClipperUiState> = _uiState.asStateFlow()

    fun onUrlChanged(newUrl: String) {
        _uiState.update {
            it.copy(
                urlInput = newUrl,
                urlValidation = YouTubeUrlParser.parse(newUrl),
                analysisStatus = if (it.analysisStatus is AnalysisStatus.Error) AnalysisStatus.Idle else it.analysisStatus
            )
        }
    }

    fun onPasteUrl(pastedText: String) {
        val cleaned = pastedText.trim()
        _uiState.update {
            it.copy(
                urlInput = cleaned,
                urlValidation = YouTubeUrlParser.parse(cleaned),
                analysisStatus = AnalysisStatus.Idle
            )
        }
    }

    fun onClearUrl() {
        _uiState.update {
            it.copy(
                urlInput = "",
                urlValidation = YouTubeUrlValidationResult.Empty,
                analysisStatus = AnalysisStatus.Idle
            )
        }
    }

    fun onSelectAspectRatio(aspectRatio: String) {
        _uiState.update { it.copy(selectedAspectRatio = aspectRatio) }
    }

    fun findClipsFromYouTubeUrl() {
        val validation = YouTubeUrlParser.parse(_uiState.value.urlInput)
        _uiState.update { it.copy(urlValidation = validation) }

        val valid = validation as? YouTubeUrlValidationResult.Valid ?: run {
            val errMsg = (validation as? YouTubeUrlValidationResult.Invalid)?.reason
                ?: "Paste a valid YouTube URL first."
            _uiState.update { it.copy(analysisStatus = AnalysisStatus.Error(errMsg)) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    analysisStatus = AnalysisStatus.FetchingMetadata,
                    activeLocalVideo = null,
                    clipExportStatus = ClipExportStatus.Idle
                )
            }

            val metadataResult = youtubeRepository.fetchPublicMetadata(valid.videoId)
            val metadata = metadataResult.getOrElse { err ->
                _uiState.update {
                    it.copy(
                        analysisStatus = AnalysisStatus.Error(
                            err.message ?: "Unable to retrieve YouTube metadata."
                        )
                    )
                }
                return@launch
            }

            val aspectRatio = _uiState.value.selectedAspectRatio
            val previewProject = ClipProject(
                userId = currentUserId,
                sourceType = ClipProject.SOURCE_YOUTUBE,
                videoId = metadata.videoId,
                sourceUrl = valid.normalizedWatchUrl,
                title = metadata.title,
                channelName = metadata.channelName,
                thumbnailUrl = metadata.thumbnailUrl,
                durationSeconds = metadata.durationSeconds,
                aspectRatio = aspectRatio,
                status = ClipProject.STATUS_PREVIEW
            )

            _uiState.update {
                it.copy(
                    activeProject = previewProject,
                    usedDataApiKey = metadata.usedDataApiKey,
                    analysisStatus = AnalysisStatus.AnalyzingVideo,
                    strategyStatus = StrategyStatus.Idle
                )
            }

            val clipsResult = aiRepository.analyzeYouTubeVideo(metadata, aspectRatio)
            val clips = clipsResult.getOrElse { err ->
                // Save preview project even if AI analysis hits an error, while transparently surfacing error
                val savedId = projectRepository.createProject(previewProject).getOrNull().orEmpty()
                _uiState.update {
                    it.copy(
                        activeProject = previewProject.copy(id = savedId),
                        analysisStatus = AnalysisStatus.Error(
                            err.message ?: "AI video analysis failed."
                        )
                    )
                }
                return@launch
            }

            val analyzedProject = previewProject.copy(
                status = ClipProject.STATUS_ANALYZED,
                clips = clips
            )
            val createdId = projectRepository.createProject(analyzedProject).getOrNull().orEmpty()
            val finalProject = analyzedProject.copy(id = createdId)

            _uiState.update {
                it.copy(
                    activeProject = finalProject,
                    selectedClipId = clips.firstOrNull()?.clipId,
                    analysisStatus = AnalysisStatus.Ready(
                        "Found ${clips.size} high-retention Short clips via Gemini 4 Argon."
                    )
                )
            }
        }
    }

    fun onLocalVideoSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    analysisStatus = AnalysisStatus.FetchingMetadata,
                    clipExportStatus = ClipExportStatus.Idle
                )
            }

            val inspectResult = videoProcessor.inspectLocalVideo(uri)
            val localInfo = inspectResult.getOrElse { err ->
                _uiState.update {
                    it.copy(
                        analysisStatus = AnalysisStatus.Error(
                            err.message ?: "Could not inspect selected local video."
                        )
                    )
                }
                return@launch
            }

            val aspectRatio = _uiState.value.selectedAspectRatio
            val localProject = ClipProject(
                userId = currentUserId,
                sourceType = ClipProject.SOURCE_LOCAL,
                videoId = "local_${localInfo.displayName.hashCode().toUInt()}",
                sourceUrl = localInfo.displayName,
                title = localInfo.displayName.substringBeforeLast('.').ifBlank { "Local Video" },
                channelName = "On-Device Local File",
                thumbnailUrl = "",
                durationSeconds = localInfo.durationSeconds,
                aspectRatio = aspectRatio,
                status = ClipProject.STATUS_PREVIEW
            )

            _uiState.update {
                it.copy(
                    activeProject = localProject,
                    activeLocalVideo = localInfo,
                    analysisStatus = AnalysisStatus.AnalyzingVideo,
                    strategyStatus = StrategyStatus.Idle
                )
            }

            val clipsResult = aiRepository.analyzeLocalVideo(localInfo, aspectRatio)
            val clips = clipsResult.getOrElse { err ->
                val savedId = projectRepository.createProject(localProject).getOrNull().orEmpty()
                _uiState.update {
                    it.copy(
                        activeProject = localProject.copy(id = savedId),
                        analysisStatus = AnalysisStatus.Error(
                            err.message ?: "AI local video analysis failed."
                        )
                    )
                }
                return@launch
            }

            val analyzedProject = localProject.copy(
                status = ClipProject.STATUS_ANALYZED,
                clips = clips
            )
            val createdId = projectRepository.createProject(analyzedProject).getOrNull().orEmpty()
            val finalProject = analyzedProject.copy(id = createdId)

            _uiState.update {
                it.copy(
                    activeProject = finalProject,
                    selectedClipId = clips.firstOrNull()?.clipId,
                    analysisStatus = AnalysisStatus.Ready(
                        "Inspected local file (${localInfo.width}x${localInfo.height}) and found ${clips.size} Short clips."
                    )
                )
            }
        }
    }

    fun openProject(project: ClipProject) {
        _uiState.update {
            it.copy(
                activeProject = project,
                activeLocalVideo = if (project.sourceType == ClipProject.SOURCE_LOCAL) it.activeLocalVideo else null,
                selectedClipId = project.clips.firstOrNull()?.clipId,
                selectedAspectRatio = project.aspectRatio,
                strategyStatus = if (project.strategyNotes.isNotBlank()) {
                    StrategyStatus.Success(project.strategyNotes)
                } else {
                    StrategyStatus.Idle
                },
                analysisStatus = AnalysisStatus.Idle,
                clipExportStatus = ClipExportStatus.Idle
            )
        }
    }

    fun selectClip(clipId: String) {
        _uiState.update { it.copy(selectedClipId = clipId) }
    }

    fun exportLocalClip(clip: ClipSuggestion) {
        val localInfo = _uiState.value.activeLocalVideo ?: run {
            _uiState.update {
                it.copy(
                    clipExportStatus = ClipExportStatus.Error(
                        "Select the local video file again to trim and export MP4 on-device."
                    )
                )
            }
            return
        }
        val project = _uiState.value.activeProject ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(clipExportStatus = ClipExportStatus.Exporting(clip.clipId, 0.05f))
            }
            val outputFile = File(
                cacheDir,
                "clipper_${clip.clipId}_${System.currentTimeMillis()}.mp4"
            )
            val startMs = clip.startSeconds * 1000L
            val endMs = (clip.endSeconds * 1000L).coerceAtMost(localInfo.durationMs.coerceAtLeast(startMs + 1000L))

            val exportResult = videoProcessor.trimLocalClip(
                sourceUri = localInfo.uri,
                startMs = startMs,
                endMs = endMs,
                aspectRatio = project.aspectRatio,
                outputFile = outputFile,
                onProgress = { progress ->
                    _uiState.update {
                        it.copy(clipExportStatus = ClipExportStatus.Exporting(clip.clipId, progress))
                    }
                }
            )

            exportResult.fold(
                onSuccess = { exported ->
                    if (project.id.isNotBlank()) {
                        projectRepository.updateProjectClips(
                            projectId = project.id,
                            clips = project.clips,
                            status = ClipProject.STATUS_CLIPPED
                        )
                    }
                    _uiState.update {
                        it.copy(
                            activeProject = it.activeProject?.copy(status = ClipProject.STATUS_CLIPPED),
                            clipExportStatus = ClipExportStatus.Exported(clip.clipId, exported)
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            clipExportStatus = ClipExportStatus.Error(
                                err.message ?: "Unable to trim local clip."
                            )
                        )
                    }
                }
            )
        }
    }

    fun onStrategyPromptChanged(prompt: String) {
        _uiState.update { it.copy(strategyPrompt = prompt) }
    }

    fun generateDeepStrategy(customPrompt: String? = null) {
        val project = _uiState.value.activeProject ?: return
        val prompt = (customPrompt ?: _uiState.value.strategyPrompt).trim()
            .ifBlank { "Create a high-retention Shorts hook script, pacing plan, and 3-part Shorts series strategy." }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    strategyPrompt = prompt,
                    strategyStatus = StrategyStatus.Thinking
                )
            }
            var finalText = ""
            aiRepository.streamDeepStrategy(project, prompt)
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            strategyStatus = StrategyStatus.Error(
                                error.message ?: "Deep strategy generation failed."
                            )
                        )
                    }
                }
                .collect { accumulated ->
                    finalText = accumulated
                    _uiState.update { it.copy(strategyStatus = StrategyStatus.Streaming(accumulated)) }
                }

            if (finalText.isNotBlank()) {
                _uiState.update {
                    it.copy(
                        strategyStatus = StrategyStatus.Success(finalText),
                        activeProject = it.activeProject?.copy(strategyNotes = finalText)
                    )
                }
                if (project.id.isNotBlank()) {
                    projectRepository.updateStrategyNotes(project.id, finalText)
                }
            }
        }
    }

    fun onVoiceRecordingStarted() {
        _uiState.update { it.copy(voiceStatus = VoiceTranscriptionStatus.Recording) }
    }

    fun onVoiceRecordingStopped(audioFile: File?) {
        if (audioFile == null || !audioFile.exists()) {
            _uiState.update {
                it.copy(voiceStatus = VoiceTranscriptionStatus.Error("No audio recorded. Hold or tap to speak clearly."))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(voiceStatus = VoiceTranscriptionStatus.Transcribing) }
            val bytes = runCatching { audioFile.readBytes() }.getOrElse { ByteArray(0) }
            audioFile.delete()

            val result = aiRepository.transcribeAudio(bytes, "audio/mp4")
            result.fold(
                onSuccess = { transcript ->
                    _uiState.update { state ->
                        val merged = if (state.strategyPrompt.isBlank()) {
                            transcript
                        } else {
                            "${state.strategyPrompt.trim()} $transcript"
                        }
                        state.copy(
                            strategyPrompt = merged,
                            voiceStatus = VoiceTranscriptionStatus.Idle
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            voiceStatus = VoiceTranscriptionStatus.Error(
                                err.message ?: "Audio transcription failed."
                            )
                        )
                    }
                }
            )
        }
    }

    fun onVoiceError(message: String) {
        _uiState.update { it.copy(voiceStatus = VoiceTranscriptionStatus.Error(message)) }
    }

    fun deleteProject(projectId: String) {
        if (projectId.isBlank()) return
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
            _uiState.update { state ->
                if (state.activeProject?.id == projectId) {
                    state.copy(activeProject = null, activeLocalVideo = null)
                } else {
                    state
                }
            }
        }
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0L) return "0:00"
        val hrs = seconds / 3600L
        val mins = (seconds % 3600L) / 60L
        val secs = seconds % 60L
        return if (hrs > 0L) {
            String.format("%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%d:%02d", mins, secs)
        }
    }

    private companion object {
        const val TAG = "ClipperVM"
    }
}
