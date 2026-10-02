package com.example.walletwise.data.repository

import com.example.walletwise.domain.result.RepositoryErrorCode
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseRepositoryErrorMapperTest {
    @Test
    fun timeoutIsReportedAsNetworkFailureForBoundedRecurringRetry() = runBlocking {
        var timeout: Throwable? = null
        try {
            withTimeout(1L) { awaitCancellation() }
        } catch (error: Throwable) {
            timeout = error
        }

        assertTrue(timeout is TimeoutCancellationException)
        assertEquals(
            RepositoryErrorCode.NETWORK,
            requireNotNull(timeout).toRepositoryError("fallback").code
        )
    }
}
