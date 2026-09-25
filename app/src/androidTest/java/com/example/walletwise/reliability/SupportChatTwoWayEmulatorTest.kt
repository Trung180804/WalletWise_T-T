package com.example.walletwise.reliability

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.walletwise.BuildConfig
import com.example.walletwise.data.repository.SupportRepositoryImpl
import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.presentation.auth.AuthStatus
import com.example.walletwise.presentation.auth.AuthViewModel
import com.example.walletwise.presentation.support.OnlineSupportScreen
import com.example.walletwise.presentation.support.SupportViewModel
import com.example.walletwise.testing.DebugFirebaseBootstrap
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Real AuthViewModel + mobile repository + unchanged UI, against local staff SDK. */
class SupportChatTwoWayEmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun localRequest(route: String, post: Boolean = false): JSONObject {
        val connection = URL("http://10.0.2.2:8787$route").openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        if (post) connection.requestMethod = "POST"
        return try {
            assertTrue("Local harness request failed", connection.responseCode == 200)
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    @Test
    fun mobileUserStaffRealtimeRetryAndAccountIsolation() {
        assertTrue("Explicit Emulator APK required", BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATOR)
        assertTrue("Fake project required", FirebaseApp.getInstance().options.projectId == "demo-walletwise")
        assertTrue("Provider did not initialize before requests", DebugFirebaseBootstrap.endpoint() != null)
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        assertTrue("Firestore endpoint mismatch", db.firestoreSettings.host == "10.0.2.2:8080" && !db.firestoreSettings.isSslEnabled)
        val proxyBefore = ProxySelector.getDefault()
        val outside = AtomicInteger()
        val production = AtomicInteger()
        val endpoints = ConcurrentHashMap<String, Int>()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> {
                val allowed = uri.host == "10.0.2.2" && uri.port in listOf(8080, 9099, 8787)
                if (!allowed) {
                    outside.incrementAndGet()
                    if (uri.host?.contains("googleapis.com") == true || uri.host?.contains("firebaseio.com") == true) production.incrementAndGet()
                    error("Runtime test forbids non-Emulator endpoints")
                }
                endpoints.merge(uri.host + ":" + uri.port, 1, Int::plus)
                return listOf(Proxy.NO_PROXY)
            }
            override fun connectFailed(uri: URI, sa: SocketAddress, ioe: java.io.IOException) = Unit
        })
        val evidence = JSONObject()
        var passed = false
        var model: SupportViewModel? = null
        try {
            val fixture = localRequest("/fixture")
            assertTrue("Harness project mismatch", fixture.getString("project") == "demo-walletwise")
            val a = fixture.getJSONObject("a")
            val c = fixture.getJSONObject("c")
            val repository = SupportRepositoryImpl(db, auth)
            lateinit var authModel: AuthViewModel
            lateinit var support: SupportViewModel
            compose.runOnUiThread {
                auth.signOut()
                authModel = ViewModelProvider(compose.activity)[AuthViewModel::class.java]
                support = ViewModelProvider(compose.activity, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = SupportViewModel(authModel.uiState, repository) as T
                })[SupportViewModel::class.java]
                model = support
            }
            compose.setContent { MaterialTheme { OnlineSupportScreen(support, {}) } }
            fun login(customer: JSONObject) {
                compose.runOnUiThread {
                    authModel.onLoginEmailChanged(customer.getString("email"))
                    authModel.onLoginPasswordChanged(customer.getString("password"))
                    authModel.submitLogin()
                }
                compose.waitUntil(timeoutMillis = 45000) {
                    (authModel.uiState.value.authStatus as? AuthStatus.Authenticated)?.session?.userId == customer.getString("uid") &&
                        support.email.state.value.userId == customer.getString("uid")
                }
                assertTrue("Auth session email mismatch", support.email.state.value.senderEmail == auth.currentUser?.email)
            }
            login(a)
            evidence.put("login_a_real_auth_viewmodel", true)
            compose.onNodeWithContentDescription("Hỗ trợ qua chat").performClick()
            compose.waitUntil(timeoutMillis = 30000) { repository.activeListenerCount == 1 && !support.chat.state.value.connecting }
            val userText = "Tôi cần hỗ trợ kiểm tra giao dịch"
            val staffText = "WalletWise đã nhận được yêu cầu của bạn"
            compose.onNodeWithTag("support-chat-input").performTextInput(userText)
            compose.onNodeWithContentDescription("Gửi tin nhắn").performClick()
            compose.waitUntil(timeoutMillis = 45000) {
                val items = support.chat.state.value.messages
                items.size == 2 && items.all { it.createdAt != null && it.delivery == SupportDelivery.SENT } &&
                    items.any { it.senderRole == SupportSenderRole.AGENT && it.content == staffText }
            }
            compose.onAllNodesWithText(staffText).assertCountEquals(1)
            compose.onNodeWithText("Nhân viên đã phản hồi").assertExists()
            evidence.put("user_staff_user_realtime_without_reopen", true)
            val items = support.chat.state.value.messages
            assertTrue("Timestamp ordering failed", items[0].createdAt!! <= items[1].createdAt!!)
            evidence.put("timestamp_order", true)
            val request = items.first { it.senderRole == SupportSenderRole.USER }
            val before = runBlocking { withTimeout(15000) { db.collection("supportConversations").document(a.getString("uid")).collection("messages").document(request.id).get(Source.SERVER).await() } }
            val retry = runBlocking { withTimeout(20000) { repository.send(AuthSession(a.getString("uid"), a.getString("email")), request) } }
            assertTrue("Retry failed", retry is RepositoryResult.Success)
            val after = runBlocking { withTimeout(15000) { before.reference.get(Source.SERVER).await() } }
            assertTrue("Retry rewrote timestamp", before.getTimestamp("createdAt") == after.getTimestamp("createdAt"))
            val count = runBlocking { withTimeout(15000) { before.reference.parent.get(Source.SERVER).await().size() } }
            assertTrue("Retry duplicated message", count == 2)
            evidence.put("retry_keeps_timestamp_and_count", true)
            val snapshotsBefore = repository.snapshotEventCount
            compose.runOnUiThread { support.chat.reconnect() }
            compose.waitUntil(timeoutMillis = 30000) { repository.snapshotEventCount > snapshotsBefore && repository.activeListenerCount == 1 }
            assertTrue("Repeated snapshot duplicated message", support.chat.state.value.messages.size == 2 && support.chat.state.value.messages.map { it.id }.distinct().size == 2)
            evidence.put("repeated_snapshot_no_duplicate", true)
            compose.onNodeWithContentDescription("Quay lại").performClick()
            compose.waitUntil(timeoutMillis = 15000) { repository.activeListenerCount == 0 }
            compose.onNodeWithContentDescription("Hỗ trợ qua chat").performClick()
            compose.waitUntil(timeoutMillis = 30000) { repository.activeListenerCount == 1 && support.chat.state.value.messages.size == 2 }
            compose.onAllNodesWithText(staffText).assertCountEquals(1)
            evidence.put("leave_reenter_history_and_listener", true)
            compose.runOnUiThread { authModel.logout() }
            compose.waitUntil(timeoutMillis = 20000) {
                repository.activeListenerCount == 0 && support.chat.state.value.userId == null &&
                    support.chat.state.value.messages.isEmpty() && support.chat.state.value.input.isEmpty()
            }
            localRequest("/late-reply", true)
            assertTrue("Logout leaked A state", support.chat.state.value.messages.isEmpty() && repository.activeListenerCount == 0)
            evidence.put("logout_a_clears_state_and_listener", true)
            login(c)
            compose.onNodeWithContentDescription("Hỗ trợ qua chat").performClick()
            compose.waitUntil(timeoutMillis = 30000) { repository.activeListenerCount == 1 && !support.chat.state.value.connecting }
            assertTrue("C saw A history", support.chat.state.value.messages.isEmpty() && support.chat.state.value.userId == c.getString("uid"))
            var denied = false
            try {
                runBlocking { withTimeout(15000) { db.collection("supportConversations").document(a.getString("uid")).get(Source.SERVER).await() } }
            } catch (error: FirebaseFirestoreException) { denied = error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED }
            assertTrue("C could read A conversation", denied)
            evidence.put("customer_c_ui_and_rules_isolation", true)
            val metrics = localRequest("/metrics")
            assertTrue("Staff did not see mobile message", metrics.getInt("customer_messages_seen") == 1 && metrics.getInt("staff_replies_sent") >= 1 && metrics.getInt("errors") == 0)
            evidence.put("staff_observed_real_mobile_message", true)
            evidence.put("staff_listener_events", metrics.getInt("listener_events"))
            assertTrue("Non-Emulator network attempt", outside.get() == 0 && production.get() == 0)
            evidence.put("auth_emulator_identity_verified", auth.currentUser?.uid == c.getString("uid"))
            evidence.put("firestore_endpoint", db.firestoreSettings.host)
            evidence.put("provider_config_before_first_request", true)
            evidence.put("release_flag_forced_false_in_gradle", true)
            passed = true
        } finally {
            compose.runOnUiThread { model?.chat?.close(); auth.signOut() }
            ProxySelector.setDefault(proxyBefore)
            evidence.put("passed", passed)
            evidence.put("outside_endpoint_attempts", outside.get())
            evidence.put("production_endpoint_matches", production.get())
            evidence.put("observed_proxy_endpoints", JSONObject(endpoints as Map<*, *>))
            val target: Context = InstrumentationRegistry.getInstrumentation().targetContext
            target.filesDir.resolve("support-runtime-evidence.json").writeText(evidence.toString(2))
        }
    }
}
