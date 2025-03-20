package net.rpcs3.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf

object AlertDialogQueue {
    val dialogs = mutableStateListOf<DialogData>()

    fun showDialog(
        title: String,
        message: String,
        onConfirm: () -> Unit = {},
        onDismiss: (() -> Unit)? = null,
        confirmText: String = "OK",
        dismissText: String = "Cancel"
    ) {
        dialogs.add(DialogData(title, message, onConfirm, onDismiss, confirmText, dismissText))
    }

    private fun dismissDialog() {
        if (dialogs.isNotEmpty()) {
            dialogs.removeAt(0)
        }
    }

    @Composable
    fun AlertDialogQueue.AlertDialog() {
        if (dialogs.isEmpty()) return

        val dialog = dialogs.first()
        var visible by remember { mutableStateOf(true) }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            AlertDialog(
                onDismissRequest = {
                    visible = false
                    dialog.onDismiss?.invoke()
                    dismissDialog()
                },
                title = { Text(dialog.title) },
                text = { Text(dialog.message) },
                confirmButton = {
                    TextButton(onClick = {
                        visible = false
                        dialog.onConfirm()
                        dismissDialog()
                    }) {
                        Text(dialog.confirmText)
                    }
                },
                dismissButton = dialog.onDismiss?.let {
                    {
                        TextButton(onClick = {
                            visible = false
                            it()
                            dismissDialog()
                        }) {
                            Text(dialog.dismissText)
                        }
                    }
                }
            )
        }
    }
}

data class DialogData(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit,
    val onDismiss: (() -> Unit)?,
    val confirmText: String,
    val dismissText: String
)
