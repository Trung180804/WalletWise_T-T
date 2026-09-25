package com.example.walletwise.reliability

import com.example.walletwise.BuildConfig
import com.example.walletwise.data.draft.AndroidDraftDateTimeProvider
import com.example.walletwise.data.repository.TransactionRepositoryImpl
import com.example.walletwise.data.repository.FinancialMappingRepositoryImpl
import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.repository.ImageUploader
import com.example.walletwise.domain.service.LocalTransactionTextAnalyzer
import com.example.walletwise.domain.usecase.AddTransactionUseCase
import com.example.walletwise.presentation.transaction.TransactionDraftPresenter
import com.example.walletwise.presentation.transaction.TransactionSessionController
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test

class AssistantTransactionEmulatorTest {
    @Test fun completeTextAutomaticallyWritesThroughExistingSessionWithStableDocumentId(): Unit = runBlocking {
        assertTrue(BuildConfig.USE_FIREBASE_EMULATOR)
        assertEquals("demo-walletwise",FirebaseApp.getInstance().options.projectId)
        val auth=FirebaseAuth.getInstance()
        auth.signOut()
        val uid=requireNotNull(auth.signInAnonymously().await().user).uid
        val repository=TransactionRepositoryImpl()
        val uploader=object:ImageUploader { override suspend fun upload(image:ImageUpload):Result<String> = error("No receipt upload allowed") }
        val writer=AddTransactionUseCase(repository,uploader)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        val session=TransactionSessionController(scope,repository)
        val presenter=TransactionDraftPresenter(scope,LocalTransactionTextAnalyzer(AndroidDraftDateTimeProvider()),MutableStateFlow(DefaultCategories),{ writer(AddTransactionInput(it)) })
        try {
            withContext(Dispatchers.Main) { session.setUserId(uid); presenter.setUserId(uid); presenter.submitAutomatically("Hôm nay mua đồ ăn 50 nghìn"); presenter.submitAutomatically("Hôm nay mua đồ ăn 50 nghìn") }
            val draft=withTimeout(15000) { presenter.state.first { it.draft!=null }.draft!! }
            assertEquals(50000L,draft.amount)
            withTimeout(20000) { presenter.state.first { it.savedDraftId==draft.id } }
            withTimeout(20000) { session.transactions.first { rows -> rows.any { it.id==draft.id } } }
            withContext(Dispatchers.Main) { presenter.submitAutomatically("Hôm nay mua đồ ăn 50 nghìn") }
            assertTrue(writer(AddTransactionInput(requireNotNull(draft.transaction()))).getOrThrow())
            val docs=FirebaseFirestore.getInstance().collection("users").document(uid).collection("transactions").get().await().documents
            assertEquals(1,docs.count { it.id==draft.id })
            val mapping=FinancialMappingRepositoryImpl()
            assertTrue(mapping.save(uid,BudgetRule.JARS,mapOf("Ăn uống" to "necessities")).getOrThrow())
            assertTrue(mapping.save(uid,BudgetRule.FIFTY_THIRTY_TWENTY,mapOf("Ăn uống" to "needs")).getOrThrow())
            assertEquals("necessities",mapping.load(uid,BudgetRule.JARS).getOrThrow()["Ăn uống"])
            assertEquals("needs",mapping.load(uid,BudgetRule.FIFTY_THIRTY_TWENTY).getOrThrow()["Ăn uống"])
            withContext(Dispatchers.Main) { presenter.setUserId(null); session.setUserId(null) }
            assertNull(presenter.state.value.draft); assertTrue(session.transactions.value.isEmpty())
        } finally { withContext(Dispatchers.Main) { presenter.close(); session.close() }; scope.cancel() }
    }
}
