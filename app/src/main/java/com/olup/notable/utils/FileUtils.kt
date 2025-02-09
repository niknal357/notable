package com.olup.notable.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.olup.notable.views.DatabasePermissionManager
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream


// adapted from:
// https://stackoverflow.com/questions/71241337/copy-image-from-uri-in-another-folder-with-another-name-in-kotlin-android
fun createFileFromContentUri(context: Context, fileUri: Uri): File {
    var fileName = ""

    // Get the display name of the file
    context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        fileName = cursor.getString(nameIndex)
    }

    // Extract the MIME type if needed
    val fileType: String? = context.contentResolver.getType(fileUri)

    // Open the input stream
    val iStream: InputStream = context.contentResolver.openInputStream(fileUri)!!

    // Set up the output file destination

    val outputDir = File(DatabasePermissionManager.ensureDatabaseFolder(), "images")
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }
    val outputFile = File(outputDir, fileName)

    // Copy the input stream to the output file
    copyStreamToFile(iStream, outputFile)
    iStream.close()
    return outputFile
}

fun copyStreamToFile(inputStream: InputStream, outputFile: File) {
    inputStream.use { input ->
        FileOutputStream(outputFile).use { output ->
            val buffer = ByteArray(4 * 1024) // buffer size
            while (true) {
                val byteCount = input.read(buffer)
                if (byteCount < 0) break
                output.write(buffer, 0, byteCount)
            }
            output.flush()
        }
    }
}
