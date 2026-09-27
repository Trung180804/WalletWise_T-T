package com.example.walletwise.presentation.support

import com.example.walletwise.data.repository.CallbackSupportRepository
import com.example.walletwise.domain.repository.CallbackSupportService
import com.example.walletwise.presentation.auth.ConnectedAuthPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SupportChatSession(
    scope: CoroutineScope,
    auth: ConnectedAuthPresenter,
    service: CallbackSupportService?,
    imagePicker: CallbackSupportImagePicker?,
    newId: () -> String,
    now: () -> Long
) {
    val chat = SupportChatPresenter(scope, CallbackSupportRepository(service), newId, now)
    val images = SupportImagePickerPresenter(imagePicker, newId)
    private val authJob = scope.launch {
        auth.state.map { it.session }.distinctUntilChanged().collect { session ->
            chat.bind(session)
            images.clear()
        }
    }

    fun dispose() {
        authJob.cancel()
        images.close()
        chat.close()
    }
}
