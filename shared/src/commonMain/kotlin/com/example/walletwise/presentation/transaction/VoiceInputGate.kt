package com.example.walletwise.presentation.transaction

enum class MicrophonePermission { GRANTED, DENIED, PERMANENTLY_DENIED }

/** Accept only the recognizer result belonging to this screen and user. */
class VoiceInputGate {
    data class Request(val version: Long, val userId: String)
    private var version = 0L
    fun begin(userId: String?, permission: MicrophonePermission): Request? =
        if (userId == null || permission != MicrophonePermission.GRANTED) null else Request(++version, userId)
    fun result(request: Request?, userId: String?, speech: String?): String? =
        speech?.trim()?.takeIf { it.isNotBlank() && request != null && request.version == version && request.userId == userId }
    fun cancel() { version++ }
}
