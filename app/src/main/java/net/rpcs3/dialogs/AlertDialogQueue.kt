package net.rpcs3.dialogs

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
        confirmText: String = android.R.string.ok,
        dismissText: String = android.R.string.cancel
    ) {
        dialogs.add(DialogData(title, message, onConfirm, onDismiss, confirmText, dismissText))
    }

    private fun dismissDialog() {
        if (dialogs.isNotEmpty()) {
            dialogs.removeAt(0)
        }
    }

    @Composable
    fun AlertDialog() {
        if (dialogs.isEmpty()) {
            return
        }

        val dialog = dialogs.first()
        val onDismiss = dialog.onDismiss

        AlertDialog(
            onDismissRequest = {
                dialog.onDismiss?.invoke()
                dismissDialog()
            },
            title = { Text(dialog.title) },
            text = { Text(dialog.message) },
            confirmButton = {
                TextButton(onClick = {
                    dialog.onConfirm()
                    dismissDialog()
                }) {
                    Text(dialog.confirmText)
                }
            },
            dismissButton = if (onDismiss == null) null else ({
                TextButton(onClick = {
                    onDismiss()
                    dismissDialog()
                }) {
                    Text(dialog.dismissText)
                }
            })
        )
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
