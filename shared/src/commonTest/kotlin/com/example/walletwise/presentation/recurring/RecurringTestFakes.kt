package com.example.walletwise.presentation.recurring

import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.repository.CategoryRepository
import com.example.walletwise.domain.repository.RecurringExecutionResult
import com.example.walletwise.domain.repository.RecurringTransactionRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.RecurringDateTimeProvider
import com.example.walletwise.domain.service.RecurringExecutionNotifier
import com.example.walletwise.domain.service.RecurringPlatformScheduleResult
import com.example.walletwise.domain.service.RecurringPlatformScheduler
import com.example.walletwise.domain.service.RecurringScheduleCalculator
import com.example.walletwise.domain.service.RecurringScheduleRequest
import com.example.walletwise.domain.service.ReminderLocalDate
import com.example.walletwise.domain.service.ReminderLocalDateTime
import com.example.walletwise.domain.service.ReminderLocalTime
import com.example.walletwise.domain.service.ReminderPermission
import com.example.walletwise.domain.service.ReminderPermissionGateway
import com.example.walletwise.domain.service.ReminderPermissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val fakeError = RepositoryError(RepositoryErrorCode.NETWORK, "offline")

internal class FakeRecurringDateTimeProvider(
    var now: ReminderLocalDateTime = ReminderLocalDateTime(
        ReminderLocalDate(2026, 9, 5),
        ReminderLocalTime(7, 0)
    ),
    var epoch: Long = 1_789_000_000_000L
) : RecurringDateTimeProvider {
    override fun currentLocalDateTime(): ReminderLocalDateTime = now
    override fun toEpochMilliseconds(dateTime: ReminderLocalDateTime): Long = epoch
    override fun currentEpochMilliseconds(): Long = epoch
}

internal class FakeRecurringRepository(
    var sessionUserId: String = "user"
) : RecurringTransactionRepository {
    private val flows = mutableMapOf<String, MutableStateFlow<RepositoryResult<List<RecurringTransaction>>>>()
    private val executionMutex = Mutex()
    val rules = mutableMapOf<String, RecurringTransaction>()
    val transactionIds = mutableSetOf<String>()
    var observeCalls = 0
    var activeCollectors = 0
    var maxActiveCollectors = 0
    var addFailure = false
    var updateFailure = false
    var deleteFailure = false
    var toggleFailure = false
    var executeFailuresBeforeCommit = 0
    var responseFailuresAfterCommit = 0
    var executeInvocations = 0

    override fun observeRecurringTransactions(
        userId: String
    ): Flow<RepositoryResult<List<RecurringTransaction>>> {
        observeCalls++
        val source = flows.getOrPut(userId) { MutableStateFlow(RepositoryResult.Success(emptyList())) }
        return flow {
            activeCollectors++
            maxActiveCollectors = maxOf(maxActiveCollectors, activeCollectors)
            try {
                source.collect { emit(it) }
            } finally {
                activeCollectors--
            }
        }
    }

    fun emit(userId: String, recurring: List<RecurringTransaction>) {
        recurring.forEach { rules[it.id] = it }
        flows.getOrPut(userId) { MutableStateFlow(RepositoryResult.Success(emptyList())) }.value =
            RepositoryResult.Success(recurring)
    }

    fun emitFailure(userId: String, error: RepositoryError = fakeError) {
        flows.getOrPut(userId) { MutableStateFlow(RepositoryResult.Success(emptyList())) }.value =
            RepositoryResult.Failure(error)
    }

    override suspend fun getRecurringTransactions(userId: String): RepositoryResult<List<RecurringTransaction>> =
        if (userId != sessionUserId) RepositoryResult.Failure(notAuthenticated())
        else RepositoryResult.Success(rules.values.filter { it.userId == userId })

    override suspend fun addRecurringTransaction(
        userId: String,
        recurring: RecurringTransaction
    ): RepositoryResult<Unit> {
        if (addFailure) return RepositoryResult.Failure(fakeError)
        if (userId != sessionUserId) return RepositoryResult.Failure(notAuthenticated())
        rules[recurring.id] = recurring
        publish(userId)
        return RepositoryResult.Success(Unit)
    }

    override suspend fun updateRecurringTransaction(
        userId: String,
        recurring: RecurringTransaction
    ): RepositoryResult<Unit> {
        if (updateFailure) return RepositoryResult.Failure(fakeError)
        if (userId != sessionUserId) return RepositoryResult.Failure(notAuthenticated())
        rules[recurring.id] = recurring
        publish(userId)
        return RepositoryResult.Success(Unit)
    }

    override suspend fun deleteRecurringTransaction(
        userId: String,
        recurringId: String
    ): RepositoryResult<Unit> {
        if (deleteFailure) return RepositoryResult.Failure(fakeError)
        if (userId != sessionUserId) return RepositoryResult.Failure(notAuthenticated())
        rules.remove(recurringId)
        publish(userId)
        return RepositoryResult.Success(Unit)
    }

    override suspend fun setRecurringTransactionEnabled(
        userId: String,
        recurringId: String,
        isEnabled: Boolean
    ): RepositoryResult<Unit> {
        if (toggleFailure) return RepositoryResult.Failure(fakeError)
        if (userId != sessionUserId) return RepositoryResult.Failure(notAuthenticated())
        rules[recurringId] = rules[recurringId]?.copy(isEnabled = isEnabled)
            ?: return RepositoryResult.Failure(fakeError)
        publish(userId)
        return RepositoryResult.Success(Unit)
    }

    override suspend fun updateLastExecutedDate(
        userId: String,
        recurringId: String,
        lastExecutedDate: String
    ): RepositoryResult<Unit> {
        if (userId != sessionUserId) return RepositoryResult.Failure(notAuthenticated())
        rules[recurringId] = rules[recurringId]?.copy(lastExecutedDate = lastExecutedDate)
            ?: return RepositoryResult.Failure(fakeError)
        publish(userId)
        return RepositoryResult.Success(Unit)
    }

    override suspend fun executeIfDue(
        userId: String,
        recurringId: String,
        now: ReminderLocalDateTime,
        executedAtEpochMilliseconds: Long
    ): RepositoryResult<RecurringExecutionResult> = executionMutex.withLock {
        executeInvocations++
        if (userId != sessionUserId) return@withLock RepositoryResult.Failure(notAuthenticated())
        if (executeFailuresBeforeCommit > 0) {
            executeFailuresBeforeCommit--
            return@withLock RepositoryResult.Failure(fakeError)
        }
        val recurring = rules[recurringId]
            ?: return@withLock RepositoryResult.Success(RecurringExecutionResult(false, null))
        if (!recurring.isEnabled) {
            return@withLock RepositoryResult.Success(RecurringExecutionResult(false, recurring))
        }
        val occurrence = RecurringScheduleCalculator.latestDueOccurrence(recurring, now)
            ?: return@withLock RepositoryResult.Success(RecurringExecutionResult(false, recurring))
        val key = RecurringScheduleCalculator.occurrenceKey(occurrence)
        if (RecurringScheduleCalculator.hasProcessed(recurring.lastExecutedDate, key)) {
            return@withLock RepositoryResult.Success(RecurringExecutionResult(false, recurring, key))
        }
        val transactionId = "${recurring.id}_$key"
        val created = transactionIds.add(transactionId)
        val updated = recurring.copy(
            lastExecutedDate = key,
            isEnabled = !RecurringScheduleCalculator.hasReachedExecutionLimit(recurring, occurrence.index)
        )
        rules[recurringId] = updated
        publish(userId)
        if (responseFailuresAfterCommit > 0) {
            responseFailuresAfterCommit--
            RepositoryResult.Failure(fakeError)
        } else {
            RepositoryResult.Success(RecurringExecutionResult(created, updated, key))
        }
    }

    private fun publish(userId: String) {
        flows[userId]?.value = RepositoryResult.Success(rules.values.filter { it.userId == userId })
    }

    private fun notAuthenticated() = RepositoryError(
        RepositoryErrorCode.NOT_AUTHENTICATED,
        "wrong uid"
    )
}

internal class FakeRecurringPlatform : RecurringPlatformScheduler, RecurringExecutionNotifier {
    val schedules = mutableListOf<RecurringScheduleRequest>()
    val retries = mutableListOf<Triple<String, String, Int>>()
    val cancellations = mutableListOf<String>()
    val notifications = mutableListOf<RecurringTransaction>()
    var scheduleFailure = false
    var cancelFailure = false
    var notificationFailure = false
    var exact = true

    override suspend fun schedule(request: RecurringScheduleRequest): RecurringPlatformScheduleResult {
        if (scheduleFailure) return RecurringPlatformScheduleResult.Failure("schedule failed")
        schedules += request
        return RecurringPlatformScheduleResult.Scheduled(exact)
    }

    override suspend fun scheduleRetry(
        userId: String,
        recurringId: String,
        retryAttempt: Int
    ): RecurringPlatformScheduleResult {
        retries += Triple(userId, recurringId, retryAttempt)
        return RecurringPlatformScheduleResult.Scheduled(exact = false)
    }

    override suspend fun cancel(recurringId: String): RecurringPlatformScheduleResult {
        if (cancelFailure) return RecurringPlatformScheduleResult.Failure("cancel failed")
        cancellations += recurringId
        return RecurringPlatformScheduleResult.Cancelled
    }

    override suspend fun transactionCreated(recurring: RecurringTransaction) {
        if (notificationFailure) error("notification failed")
        notifications += recurring
    }
}

internal class FakeCategoryRepository(
    initial: List<Category>
) : CategoryRepository {
    private val source = MutableStateFlow<RepositoryResult<List<Category>>>(RepositoryResult.Success(initial))
    override fun observeCategories(userId: String): Flow<RepositoryResult<List<Category>>> = source
    override suspend fun addCategory(userId: String, category: Category) = RepositoryResult.Success(Unit)
    override suspend fun updateCategory(userId: String, category: Category) = RepositoryResult.Success(Unit)
    override suspend fun deleteCategory(userId: String, categoryId: String) = RepositoryResult.Success(Unit)
    override suspend fun swapCategories(userId: String, first: Category, second: Category) = RepositoryResult.Success(Unit)
    override suspend fun ensureDefaultCategories(
        userId: String,
        defaults: List<Category>
    ): RepositoryResult<Boolean> = RepositoryResult.Success(false)
}

internal class FakePermissionGateway(
    var state: ReminderPermissionState = ReminderPermissionState.GRANTED
) : ReminderPermissionGateway {
    override fun permissionState(permission: ReminderPermission): ReminderPermissionState = state
}
