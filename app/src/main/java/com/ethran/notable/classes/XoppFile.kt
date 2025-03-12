package com.ethran.notable.classes

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import com.ethran.notable.SCREEN_HEIGHT
import com.ethran.notable.SCREEN_WIDTH
import com.ethran.notable.TAG
import com.ethran.notable.db.BookRepository
import com.ethran.notable.db.PageRepository
import com.ethran.notable.db.Stroke
import com.ethran.notable.modals.A4_WIDTH
import io.shipbook.shipbooksdk.Log
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter

class XoppFile(private var file: File?) {

    fun exportBook(context: Context, bookId: String) {
        val book = BookRepository(context).getById(bookId) ?: return Log.e(
            TAG,
            "Book ID($bookId) not found"
        )
        val fileName = book.title
        val tempFile = File(context.cacheDir, "$fileName.xml")

        tempFile.outputStream().use { outputStream ->
            OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                writer.write("<xournal version=\"0.4\">\n")

                book.pageIds.forEach { pageId ->
                    writePage(context, pageId, writer)
                }

                writer.write("</xournal>\n")
            }
        }

        // Compress and save as .xopp file
        compressAndSave(context, tempFile, fileName)
    }

    fun exportPage(context: Context, pageId: String) {
        val tempFile = File(context.cacheDir, "exported_page.xml")
        tempFile.outputStream().use { outputStream ->
            OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                writer.write("<xournal version=\"0.4\">\n")
                writePage(context, pageId, writer)
                writer.write("</xournal>\n")
            }
        }
        compressAndSave(context, tempFile, "exported_page")
    }

    private fun writePage(context: Context, pageId: String, writer: OutputStreamWriter) {
        val pages = PageRepository(context)
        val (page, strokes) = pages.getWithStrokeById(pageId)
        val (_, images) = pages.getWithImageById(pageId)

        val strokeHeight = if (strokes.isEmpty()) 0 else strokes.maxOf(Stroke::bottom).toInt() + 50
        val scaleFactor = A4_WIDTH.toFloat() / SCREEN_WIDTH
        val height = strokeHeight.coerceAtLeast(SCREEN_HEIGHT) * scaleFactor

        writer.write("  <page width=\"$A4_WIDTH\" height=\"$height\">\n")
        writer.write("    <layer>\n")

        for (stroke in strokes) {
            val pointsString =
                stroke.points.joinToString(" ") { "${it.x * scaleFactor} ${it.y * scaleFactor}" }
            writer.write("      <stroke tool=\"${stroke.pen}\" color=\"${getColorName(Color(stroke.color))}\" width=\"${stroke.size * scaleFactor}\">$pointsString</stroke>\n")
        }

        writer.write("    </layer>\n")

        writer.write("  </page>\n")
    }

    private fun compressAndSave(context: Context, file: File, fileName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, "$fileName.xopp")
            put(MediaStore.Files.FileColumns.MIME_TYPE, "application/x-xopp")
            put(
                MediaStore.Files.FileColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/Notable/"
            )
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            ?: throw IOException("Failed to create Media Store entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            GzipCompressorOutputStream(BufferedOutputStream(outputStream)).use { gzipOutputStream ->
                file.inputStream().copyTo(gzipOutputStream)
            }
        } ?: throw IOException("Failed to open output stream")

        file.delete()
    }

    private fun getColorName(color: Color): String {
        return when (color) {
            Color.Black -> "black"
            Color.Blue -> "blue"
            Color.Red -> "red"
            Color.Green -> "green"
            Color.DarkGray, Color.Gray -> "gray"
            Color.Cyan -> "lightblue"
            Color.Magenta -> "magenta"
            Color.Yellow -> "yellow"
            else -> "white"
        }
    }
}
