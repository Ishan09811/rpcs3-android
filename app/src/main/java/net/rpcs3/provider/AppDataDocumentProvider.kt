package net.rpcs3.provider

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import net.rpcs3.R
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.FileInputStream
import java.io.FileOutputStream

class AppDataDocumentProvider : DocumentsProvider() {
    companion object {
        private const val ROOT_ID = "root"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE
        )
    }

    private fun obtainDocumentId(file: File): String {
        val basePath = baseDirectory().absolutePath
        val fullPath = file.absolutePath
        return (ROOT_ID + "/" + fullPath.substring(basePath.length)).replace("//", "/")
    }

    private fun obtainFile(documentId: String): File {
        require(documentId.startsWith(ROOT_ID)) { "Invalid document id: $documentId" }
        return File(baseDirectory(), documentId.substring(ROOT_ID.length))
    }

    private fun context(): Context = context!!

    private fun baseDirectory(): File = context().getExternalFilesDir(null)!!

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        cursor.newRow()
            .add(Root.COLUMN_ROOT_ID, ROOT_ID)
            .add(Root.COLUMN_SUMMARY, null)
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_SUPPORTS_CREATE)
            .add(Root.COLUMN_DOCUMENT_ID, "$ROOT_ID/")
            .add(Root.COLUMN_AVAILABLE_BYTES, baseDirectory().freeSpace)
            .add(Root.COLUMN_TITLE, context().getString(R.string.app_name))
            .add(Root.COLUMN_MIME_TYPES, "*/*")
            .add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(cursor, obtainFile(documentId))
        return cursor
    }

    private fun includeFile(cursor: MatrixCursor, file: File) {
        val flags = when {
            file.isDirectory -> Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_DELETE
            else -> Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_REMOVE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_COPY or Document.FLAG_SUPPORTS_MOVE
        }

        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, obtainDocumentId(file))
            .add(Document.COLUMN_MIME_TYPE, if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream")
            .add(Document.COLUMN_FLAGS, flags)
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_SIZE, file.length())
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        obtainFile(parentDocumentId).listFiles()?.forEach { includeFile(cursor, it) }
        return cursor
    }

    override fun isChildDocument(parentDocumentId : String?, documentId : String?) : Boolean {
        return documentId?.startsWith(parentDocumentId!!) ?: false
    }

    fun File.resolveWithoutConflict(name : String) : File {
        var file = resolve(name)
        if (file.exists()) {
            var noConflictId = 1 // Makes sure two files don't have the same name by adding a number to the end
            val extension = name.substringAfterLast('.')
            val baseName = name.substringBeforeLast('.')
            while (file.exists())
                file = resolve("$baseName (${noConflictId++}).$extension")
        }
        return file
    }

    private fun copyDocument(
        sourceDocumentId : String, sourceParentDocumentId : String,
        targetParentDocumentId : String?
    ) : String? {
        if (!isChildDocument(sourceParentDocumentId, sourceDocumentId))
            throw FileNotFoundException("Couldn't copy document '$sourceDocumentId' as its parent is not '$sourceParentDocumentId'")

        return copyDocument(sourceDocumentId, targetParentDocumentId)
    }

    override fun copyDocument(sourceDocumentId : String, targetParentDocumentId : String?) : String? {
        val parent = obtainFile(targetParentDocumentId!!)
        val oldFile = obtainFile(sourceDocumentId)
        val newFile = parent.resolveWithoutConflict(oldFile.name)

        try {
            if (!(newFile.createNewFile() && newFile.setWritable(true) && newFile.setReadable(true)))
                throw IOException("Couldn't create new file")

            FileInputStream(oldFile).use { inStream ->
                FileOutputStream(newFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
        } catch (e : IOException) {
            throw FileNotFoundException("Couldn't copy document '$sourceDocumentId': ${e.message}")
        }

        return obtainDocumentId(newFile)
    }

    override fun moveDocument(
        sourceDocumentId : String, sourceParentDocumentId : String?,
        targetParentDocumentId : String?
    ) : String? {
        try {
            val newDocumentId = copyDocument(
                sourceDocumentId, sourceParentDocumentId!!,
                targetParentDocumentId
            )
            removeDocument(sourceDocumentId, sourceParentDocumentId)
            return newDocumentId
        } catch (e : FileNotFoundException) {
            throw FileNotFoundException("Couldn't move document '$sourceDocumentId'")
        }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = obtainFile(parentDocumentId)
        val file = File(parent, displayName)

        if (!parent.exists()) throw FileNotFoundException("Parent doesn't exist")

        if (mimeType == Document.MIME_TYPE_DIR) {
            if (!file.mkdirs()) throw FileNotFoundException("Error while creating directory")
        } else {
            if (!file.createNewFile()) throw FileNotFoundException("Error while creating file")
        }

        return obtainDocumentId(file)
    }

    override fun deleteDocument(documentId: String) {
        val file = obtainFile(documentId)
        if (file.exists()) {
            file.deleteRecursively()
        } else {
            throw FileNotFoundException("File not exists")
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        return ParcelFileDescriptor.open(obtainFile(documentId), ParcelFileDescriptor.parseMode(mode))
    }
}
