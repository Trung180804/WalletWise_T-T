package com.example.walletwise.presentation.recurring

import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_DAILY
import com.example.walletwise.domain.model.RECURRING_TIMES_UNLIMITED
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.service.RecurringAutomationCoordinator
import com.example.walletwise.domain.usecase.ExecuteRecurringIfDueUseCase
import com.example.walletwise.domain.validation.RecurringValidationError
import com.example.walletwise.presentation.category.CategorySessionController
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecurringUiPresenterTest {
    @Test
    fun presenterExposesLoadingEmptyRepositoryErrorAndDetailState() = runTest {
        val fixture = fixture()
        fixture.session.setUserId("user")
        assertTrue(fixture.session.state.value.isLoading)
        runCurrent()
        assertTrue(fixture.presenter.state.value.isEmpty)

        val rule = rule()
        fixture.repository.emit("user", listOf(rule))
        runCurrent()
        fixture.presenter.onOpenDetail(rule)
        assertEquals(rule, fixture.presenter.state.value.selectedDetail)
        fixture.presenter.onDismissDetail()
        assertNull(fixture.presenter.state.value.selectedDetail)

        fixture.repository.emitFailure("user")
        runCurrent()
        assertEquals(fakeError, fixture.presenter.state.value.repositoryError)
    }

    @Test
    fun formValidationWriteFailureAndSuccessfulEventPreserveExpectedState() = runTest {
        val fixture = fixture()
        fixture.session.setUserId("user")
        runCurrent()
        fixture.presenter.onOpenAdd()
        fixture.presenter.onSubmit()
        runCurrent()
        assertEquals(RecurringValidationError.TITLE_REQUIRED, fixture.presenter.state.value.validationError)
        assertEquals(RecurringScreenMode.FORM, fixture.presenter.state.value.screenMode)

        fillValidForm(fixture.presenter)
        fixture.repository.addFailure = true
        fixture.presenter.onSubmit()
        assertTrue(fixture.presenter.state.value.isSaving)
        runCurrent()
        assertEquals(RecurringScreenMode.FORM, fixture.presenter.state.value.screenMode)
        assertEquals("Rent", fixture.presenter.state.value.title)
        assertIs<RecurringUiEvent.Message>(fixture.presenter.state.value.pendingEvent?.event)

        fixture.presenter.consumeEvent(fixture.presenter.state.value.pendingEvent!!.id)
        fixture.repository.addFailure = false
        fixture.presenter.onSubmit()
        runCurrent()
        assertEquals(RecurringScreenMode.LIST, fixture.presenter.state.value.screenMode)
        val envelope = fixture.presenter.state.value.pendingEvent
        assertEquals(RecurringMessage.ADDED, (envelope?.event as? RecurringUiEvent.Message)?.kind)
        fixture.presenter.consumeEvent(requireNotNull(envelope).id)
        assertNull(fixture.presenter.state.value.pendingEvent)
    }

    @Test
    fun pickerCancelDoesNotChangeCommittedValueAndCategoryListTracksThuChiState() = runTest {
        val fixture = fixture()
        fixture.session.setUserId("user")
        runCurrent()
        fixture.presenter.onOpenAdd()
        val originalDate = fixture.presenter.state.value.startDate
        fixture.presenter.onOpenPicker(RecurringPicker.DATE)
        fixture.presenter.onNextDatePickerMonth()
        fixture.presenter.onDatePickerDaySelected(20)
        fixture.presenter.onDismissPicker()
        assertEquals(originalDate, fixture.presenter.state.value.startDate)

        fixture.presenter.onTypeSelected("Thu")
        assertTrue(fixture.presenter.state.value.currentCategories.all { it.type == "Thu" })
        assertEquals("Lương", fixture.presenter.state.value.category)
        fixture.presenter.onTypeSelected("Chi")
        assertTrue(fixture.presenter.state.value.currentCategories.all { it.type == "Chi" })
        assertEquals("Hóa đơn", fixture.presenter.state.value.category)
    }

    @Test
    fun duplicateSubmitIsIgnoredAndToggleFailureRollsBackOptimisticState() = runTest {
        val fixture = fixture()
        val rule = rule()
        fixture.repository.rules[rule.id] = rule
        fixture.repository.emit("user", listOf(rule))
        fixture.session.setUserId("user")
        runCurrent()
        fixture.presenter.onOpenAdd()
        fillValidForm(fixture.presenter)
        fixture.presenter.onSubmit()
        fixture.presenter.onSubmit()
        runCurrent()
        assertEquals(2, fixture.repository.rules.size)

        fixture.repository.toggleFailure = true
        fixture.presenter.onToggle(rule, false)
        assertFalse(fixture.presenter.state.value.recurring.first { it.id == rule.id }.isEnabled)
        runCurrent()
        assertTrue(fixture.presenter.state.value.recurring.first { it.id == rule.id }.isEnabled)
    }

    @Test
    fun failedDeleteRestoresThePreviousEnabledScheduleAndKeepsConfirmationState() = runTest {
        val fixture = fixture()
        val rule = rule()
        fixture.repository.rules[rule.id] = rule
        fixture.repository.emit("user", listOf(rule))
        fixture.session.setUserId("user")
        runCurrent()
        val schedulesBeforeDelete = fixture.platform.schedules.size
        fixture.repository.deleteFailure = true

        fixture.presenter.onRequestDelete(rule)
        fixture.presenter.onConfirmDelete()
        assertEquals(rule.id, fixture.presenter.state.value.deletingRecurringId)
        runCurrent()

        assertEquals(rule, fixture.presenter.state.value.deletingRecurring)
        assertTrue(fixture.platform.schedules.size > schedulesBeforeDelete)
        assertTrue(fixture.repository.rules.containsKey(rule.id))
    }

    @Test
    fun backClosesFormFirstThenEmitsOneNavigationBackToSettings() = runTest {
        val fixture = fixture()
        fixture.session.setUserId("user")
        runCurrent()
        fixture.presenter.onOpenAdd()
        fixture.presenter.onBack()
        assertEquals(RecurringScreenMode.LIST, fixture.presenter.state.value.screenMode)
        fixture.presenter.onBack()
        val envelope = requireNotNull(fixture.presenter.state.value.pendingEvent)
        assertIs<RecurringUiEvent.NavigateBack>(envelope.event)
        fixture.presenter.consumeEvent(envelope.id)
        assertNull(fixture.presenter.state.value.pendingEvent)
    }

    private fun TestScope.fixture(): Fixture {
        val repository = FakeRecurringRepository()
        val provider = FakeRecurringDateTimeProvider()
        val platform = FakeRecurringPlatform()
        val coordinator = RecurringAutomationCoordinator(
            ExecuteRecurringIfDueUseCase(repository, provider),
            platform,
            platform,
            provider
        )
        val session = RecurringSessionController(backgroundScope, repository, coordinator)
        val categorySession = CategorySessionController(
            backgroundScope,
            FakeCategoryRepository(
                listOf(
                    Category(id = "expense", name = "Hóa đơn", type = "Chi"),
                    Category(id = "income", name = "Lương", type = "Thu")
                )
            ),
            defaults = emptyList()
        )
        categorySession.setUserId("user")
        val presenter = RecurringUiPresenter(
            backgroundScope,
            session,
            categorySession,
            repository,
            coordinator,
            provider,
            FakePermissionGateway()
        )
        return Fixture(repository, session, presenter, platform)
    }

    private fun fillValidForm(presenter: RecurringUiPresenter) {
        presenter.onTitleChanged("Rent")
        presenter.onAmountChanged("100")
        presenter.onFrequencySelected(RECURRING_FREQUENCY_DAILY)
        presenter.onTimesCountSelected(RECURRING_TIMES_UNLIMITED)
    }

    private fun rule() = RecurringTransaction(
        id = "rule",
        userId = "user",
        title = "Rent",
        amount = 100.0,
        type = "Chi",
        category = "Hóa đơn",
        paymentMethod = "Tiền mặt",
        frequency = RECURRING_FREQUENCY_DAILY,
        timesCount = RECURRING_TIMES_UNLIMITED,
        startDate = "6 thg 9, 2026",
        time = "08:00"
    )

    private data class Fixture(
        val repository: FakeRecurringRepository,
        val session: RecurringSessionController,
        val presenter: RecurringUiPresenter,
        val platform: FakeRecurringPlatform
    )
}
