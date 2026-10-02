package com.example.walletwise.presentation.transaction

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.walletwise.domain.service.MoneyInput
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Preserve the caret when a paste inserts separators, including in the middle. */
@Composable
fun rememberMoneyInput(raw: String, onRawChanged: (String) -> Unit): Pair<TextFieldValue, (TextFieldValue) -> Unit> {
    var field by remember { mutableStateOf(TextFieldValue(raw, TextRange(raw.length))) }
    val displayed = if (field.text == raw) field else TextFieldValue(raw, TextRange(raw.length))
    SideEffect { if (field.text != raw) field = displayed }
    return displayed to { value ->
        MoneyInput.edit(value.text, value.selection.start, value.selection.end)?.let { edit ->
            field = TextFieldValue(edit.raw, TextRange(edit.selectionStart, edit.selectionEnd))
            onRawChanged(edit.raw)
        }
    }
}

object GroupedMoneyTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        return TransformedText(AnnotatedString(MoneyInput.format(raw)), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = MoneyInput.originalToFormatted(raw, offset)
            override fun transformedToOriginal(offset: Int) = MoneyInput.formattedToOriginal(raw, offset)
        })
    }
}
