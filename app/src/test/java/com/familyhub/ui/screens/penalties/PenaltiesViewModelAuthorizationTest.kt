package com.familyhub.ui.screens.penalties

import android.content.Context
import com.familyhub.R
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.PenaltyRepository
import com.familyhub.domain.model.AppSettings
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PenaltiesViewModelAuthorizationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsFlow = MutableStateFlow(AppSettings(currentUserId = ACTOR_ID))
    private val membersFlow = MutableStateFlow(
        listOf(
            member(ACTOR_ID, MemberRole.ADMIN),
            member(TARGET_ID, MemberRole.CHILD),
        ),
    )
    private val penaltiesFlow = MutableStateFlow(emptyList<com.familyhub.domain.model.Penalty>())
    private val penaltyRepository: PenaltyRepository = mockk(relaxed = true)
    private val memberRepository: FamilyMemberRepository = mockk()
    private val context: Context = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { penaltyRepository.getAllPenalties() } returns penaltiesFlow
        every { memberRepository.getAllMembers() } returns membersFlow
        every { context.getString(R.string.penalties_error_only_parents) } returns "read-only"
        every { context.getString(R.string.penalties_error_target_missing) } returns "target-missing"
        every { context.getString(R.string.penalties_error_target_pending) } returns "target-pending"
        every { context.getString(R.string.penalties_error_invalid_amount) } returns "invalid-amount"
        every { context.getString(R.string.error_unknown) } returns "unknown"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createPenalty_freshActorDemoted_blocksRepositoryWrite() = runTest {
        coEvery { memberRepository.getMemberById(ACTOR_ID) } returns
            member(ACTOR_ID, MemberRole.CHILD)
        coEvery { memberRepository.getMemberById(TARGET_ID) } returns
            member(TARGET_ID, MemberRole.CHILD)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.createPenalty(TARGET_ID, "-5", "Late")
        advanceUntilIdle()

        coVerify(exactly = 0) { penaltyRepository.createPenalty(any(), any(), any(), any(), any()) }
        assertEquals("read-only", viewModel.state.value.errorMessage)
    }

    @Test
    fun createPenalty_freshTargetPending_blocksRepositoryWrite() = runTest {
        coEvery { memberRepository.getMemberById(ACTOR_ID) } returns
            member(ACTOR_ID, MemberRole.ADMIN)
        coEvery { memberRepository.getMemberById(TARGET_ID) } returns
            member(TARGET_ID, MemberRole.PENDING)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.createPenalty(TARGET_ID, "-5", null)
        advanceUntilIdle()

        coVerify(exactly = 0) { penaltyRepository.createPenalty(any(), any(), any(), any(), any()) }
        assertEquals("target-pending", viewModel.state.value.errorMessage)
    }

    @Test
    fun createPenalty_freshTargetMissing_blocksRepositoryWrite() = runTest {
        coEvery { memberRepository.getMemberById(ACTOR_ID) } returns
            member(ACTOR_ID, MemberRole.PARENT)
        coEvery { memberRepository.getMemberById(TARGET_ID) } returns null
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.createPenalty(TARGET_ID, "-5", null)
        advanceUntilIdle()

        coVerify(exactly = 0) { penaltyRepository.createPenalty(any(), any(), any(), any(), any()) }
        assertEquals("target-missing", viewModel.state.value.errorMessage)
    }

    @Test
    fun cancelPenalty_freshActorDemoted_blocksRepositoryWrite() = runTest {
        coEvery { memberRepository.getMemberById(ACTOR_ID) } returns
            member(ACTOR_ID, MemberRole.CHILD)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.cancelPenalty(PENALTY_ID)
        advanceUntilIdle()

        coVerify(exactly = 0) { penaltyRepository.cancelPenalty(any(), any()) }
        assertEquals("read-only", viewModel.state.value.errorMessage)
    }

    @Test
    fun dataEmission_preservesLocalizedMutationError() = runTest {
        coEvery { memberRepository.getMemberById(ACTOR_ID) } returns
            member(ACTOR_ID, MemberRole.ADMIN)
        coEvery { memberRepository.getMemberById(TARGET_ID) } returns null
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.createPenalty(TARGET_ID, "-5", null)
        advanceUntilIdle()
        membersFlow.value = membersFlow.value + member("another", MemberRole.CHILD)
        advanceUntilIdle()

        assertEquals("target-missing", viewModel.state.value.errorMessage)
    }

    private fun createViewModel(): PenaltiesViewModel {
        val appSettingsDataStore = mockk<AppSettingsDataStore>()
        every { appSettingsDataStore.settingsFlow } returns settingsFlow
        return PenaltiesViewModel(
            penaltyRepository = penaltyRepository,
            memberRepository = memberRepository,
            appSettingsDataStore = appSettingsDataStore,
            context = context,
        )
    }

    private fun member(id: String, role: MemberRole): FamilyMember = FamilyMember(
        id = id,
        displayName = id,
        roleLabel = role.name,
        avatarColor = "#000000",
        role = role,
    )

    private companion object {
        const val ACTOR_ID = "actor"
        const val TARGET_ID = "target"
        const val PENALTY_ID = "penalty"
    }
}
