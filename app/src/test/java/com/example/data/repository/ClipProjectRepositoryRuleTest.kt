package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.model.ClipProject
import com.example.domain.model.ClipSuggestion
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipProjectRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleProject(uid: String) = ClipProject(
        userId = uid,
        sourceType = ClipProject.SOURCE_YOUTUBE,
        videoId = "dQw4w9WgXcQ",
        sourceUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        title = "Podcast Highlights Clip Study",
        channelName = "Clipper Studio",
        thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
        durationSeconds = 360L,
        aspectRatio = ClipProject.ASPECT_9_16,
        status = ClipProject.STATUS_PREVIEW
    )

    @Test
    fun createAndObserveProject_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = ClipProjectRepository(firestore, auth)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(sampleProject(aliceUid))
        }
        assertTrue(createResult.isSuccess)
        val projectId = createResult.getOrThrow()
        assertTrue(projectId.isNotBlank())

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProjects(aliceUid).first { list -> list.any { it.id == projectId } }
        }
        assertTrue(observed.any { it.id == projectId })
    }

    @Test
    fun updateProjectClips_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = ClipProjectRepository(firestore, auth)
        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(sampleProject(aliceUid)).getOrThrow()
        }

        val clips = listOf(
            ClipSuggestion(
                clipId = "clip_1",
                title = "Opening Hook",
                startSeconds = 10L,
                endSeconds = 42L,
                hookScore = 91L,
                rationale = "High curiosity gap in first 3 seconds",
                hashtags = listOf("#shorts", "#creator")
            )
        )

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateProjectClips(projectId, clips, ClipProject.STATUS_ANALYZED)
        }
        assertTrue(updateResult.isSuccess)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getProject(projectId).getOrThrow()
        }
        assertNotNull(fetched)
        assertEquals(ClipProject.STATUS_ANALYZED, fetched?.status)
        assertEquals(1, fetched?.clips?.size)
    }

    @Test
    fun observeProjects_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ClipProjectRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createProject(sampleProject(aliceUid)).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ClipProjectRepository(firestore, auth)
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeProjects(aliceUid).first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val firestoreException = caught?.findFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun observeProjects_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = ClipProjectRepository(firestore, auth)
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeProjects("some_uid").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val firestoreException = caught?.findFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_clipper@test.com"
        const val BOB_EMAIL = "bob_clipper@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
