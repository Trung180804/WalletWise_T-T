package com.example.walletwise.reliability

import com.example.walletwise.BuildConfig
import com.example.walletwise.data.repository.TransactionRepositoryImpl
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.presentation.transaction.TransactionSessionController
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test

class LegacyFallbackSnapshotEmulatorTest {
    @Test fun cancelledLateLegacyReadCannotReplaceNewPrimarySnapshotWithEmptyList(): Unit = runBlocking {
        assertTrue(BuildConfig.USE_FIREBASE_EMULATOR)
        assertEquals("demo-walletwise", FirebaseApp.getInstance().options.projectId)
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val uid = requireNotNull(auth.signInAnonymously().await().user).uid
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val repository = TransactionRepositoryImpl(legacyLoader = {
            withContext(NonCancellable) {
                started.complete(Unit)
                try { release.await(); emptyList() } finally { finished.complete(Unit) }
            }
        })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val session = TransactionSessionController(scope, repository)
        try {
            withContext(Dispatchers.Main) { session.setUserId(uid) }
            withTimeout(20000) { started.await() }
            val transaction = Transaction("checkpoint-primary-during-legacy", uid, "Chi", "Tiền mặt", 50000.0, "Ăn uống", "Synthetic migration test", System.currentTimeMillis())
            assertTrue(repository.addTransaction(transaction).getOrThrow())
            withTimeout(20000) { session.transactions.first { it.any { tx -> tx.id == transaction.id } } }
            release.complete(Unit)
            withTimeout(20000) { finished.await() }
            // Drain queued main work after the deliberately late fallback has finished.
            withContext(Dispatchers.Main) { yield() }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(listOf(transaction.id), session.transactions.value.map { it.id })
        } finally { release.complete(Unit); withContext(Dispatchers.Main) { session.close() }; scope.cancel() }
    }
}
