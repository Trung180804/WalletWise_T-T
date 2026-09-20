package com.example.walletwise.presentation.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walletwise.data.repository.SupportRepositoryImpl
import com.example.walletwise.domain.repository.SupportRepository
import com.example.walletwise.presentation.auth.AuthProfileUiState
import com.example.walletwise.presentation.auth.AuthStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.UUID

class SupportViewModel(
    authState: StateFlow<AuthProfileUiState>,
    repository: SupportRepository = SupportRepositoryImpl()
) : ViewModel() {
    val chat = SupportChatPresenter(viewModelScope, repository, { UUID.randomUUID().toString() }, System::currentTimeMillis)
    val email = SupportEmailPresenter()
    init {
        viewModelScope.launch {
            authState.map { (it.authStatus as? AuthStatus.Authenticated)?.session }.distinctUntilChanged().collect { session ->
                chat.bind(session)
                email.bind(session)
            }
        }
    }
    override fun onCleared() { chat.close(); email.clear(); super.onCleared() }
}
