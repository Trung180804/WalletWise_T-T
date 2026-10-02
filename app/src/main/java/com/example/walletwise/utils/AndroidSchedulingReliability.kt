package com.example.walletwise.utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal data class AlarmDispatchOutcome(
    val exact: Boolean?,
    val error: Throwable? = null
)

/** Pure scheduling rules shared by the Android adapters and host-side tests. */
internal object AndroidSchedulingContract {
    const val ACTION_REMINDER_ALARM = "com.example.walletwise.action.REMINDER_ALARM"
    const val ACTION_RECURRING_ALARM = "com.example.walletwise.action.RECURRING_ALARM"

    private const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
    private const val ACTION_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
    private const val ACTION_TIME_SET = "android.intent.action.TIME_SET"
    private const val ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED"
    private const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

    val forceReconcileActions: Set<String> = setOf(
        ACTION_BOOT_COMPLETED,
        ACTION_PACKAGE_REPLACED,
        ACTION_TIME_SET,
        ACTION_TIMEZONE_CHANGED,
        ACTION_EXACT_ALARM_PERMISSION_CHANGED
    )

    fun stableRequestCode(itemId: String): Int = itemId.hashCode()

    fun reminderData(itemId: String): String = "walletwise://reminder/${encodePathSegment(itemId)}"

    fun recurringData(itemId: String): String = "walletwise://recurring/${encodePathSegment(itemId)}"

    fun retryDelayMillis(retryAttempt: Int): Long {
        require(retryAttempt in 1..5) { "Retry attempt must be between 1 and 5" }
        return (1L shl (retryAttempt - 1)) * 60_000L
    }

    fun shouldUseExactAlarm(
        sdkInt: Int,
        exactAlarmPermissionApi: Int,
        canScheduleExactAlarms: Boolean
    ): Boolean = sdkInt < exactAlarmPermissionApi || canScheduleExactAlarms

    fun shouldPostNotification(
        sdkInt: Int,
        notificationPermissionApi: Int,
        permissionGranted: Boolean
    ): Boolean = sdkInt < notificationPermissionApi || permissionGranted

    fun isForceReconcileAction(action: String?): Boolean = action in forceReconcileActions

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

/**
 * Runs an alarm operation with the same exact-to-inexact fallback used by both
 * Android schedulers, while keeping the decision independently testable.
 */
internal fun dispatchAlarm(
    canUseExact: Boolean,
    setExact: () -> Unit,
    setInexact: () -> Unit
): AlarmDispatchOutcome {
    if (!canUseExact) {
        return runCatching(setInexact).fold(
            onSuccess = { AlarmDispatchOutcome(exact = false) },
            onFailure = { AlarmDispatchOutcome(exact = null, error = it) }
        )
    }

    return try {
        setExact()
        AlarmDispatchOutcome(exact = true)
    } catch (_: SecurityException) {
        runCatching(setInexact).fold(
            onSuccess = { AlarmDispatchOutcome(exact = false) },
            onFailure = { AlarmDispatchOutcome(exact = null, error = it) }
        )
    } catch (error: Throwable) {
        AlarmDispatchOutcome(exact = null, error = error)
    }
}

internal suspend fun runBroadcastWork(
    finish: () -> Unit,
    work: suspend () -> Unit
) {
    try {
        work()
    } finally {
        finish()
    }
}
