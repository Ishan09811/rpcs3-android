package net.rpcs3.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import android.util.Log
import net.rpcs3.PrecompilerService
import net.rpcs3.PrecompilerServiceAction

object FileUtil {
    fun installPackages(context: Context, folderUri: Uri, depth: Int = 2) {
        if (depth <= 0) return
        val rootFolder = DocumentFile.fromTreeUri(context, folderUri) ?: return
        
        for (file in rootFolder.listFiles()) {
            val fileUri = file.uri ?: continue
            if (!file.isDirectory()) {
                Log.d("Install", "Installing package: ${fileUri}")
                PrecompilerService.start(context, PrecompilerServiceAction.Install, fileUri)
            } else {
                installPackages(context, fileUri, depth - 1)
            }
        }
    }

    fun saveGameFolderUri(prefs: SharedPreferences, uri: Uri) {
        prefs.edit().putString("selected_game_folder", uri.toString()).apply()
    }
}
