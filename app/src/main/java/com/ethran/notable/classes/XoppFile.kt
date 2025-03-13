package com.ethran.notable.classes

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ethran.notable.BuildConfig
import com.ethran.notable.SCREEN_HEIGHT
import com.ethran.notable.SCREEN_WIDTH
import com.ethran.notable.TAG
import com.ethran.notable.db.BookRepository
import com.ethran.notable.db.PageRepository
import com.ethran.notable.db.Stroke
import com.ethran.notable.modals.A4_WIDTH
import io.shipbook.shipbooksdk.Log
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.w3c.dom.Document
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult


object XoppFile {

    /**
     * Exports an entire book as a `.xopp` file.
     *
     * This method processes each page separately, writing the XML data
     * to a temporary file to prevent excessive memory usage. After all
     * pages are processed, the file is compressed into a `.xopp` format.
     *
     * @param context The application context.
     * @param bookId The ID of the book to export.
     */
    fun exportBook(context: Context, bookId: String) {
        val book = BookRepository(context).getById(bookId)
            ?: return Log.e(TAG, "Book ID($bookId) not found")

        val fileName = book.title
        val tempFile = File(context.cacheDir, "$fileName.xml")

        BufferedWriter(
            OutputStreamWriter(
                FileOutputStream(tempFile),
                Charsets.UTF_8
            )
        ).use { writer ->
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            writer.write("<xournal creator=\"Notable ${BuildConfig.VERSION_NAME}\" version=\"0.4\">\n")

            book.pageIds.forEach { pageId ->
                writePage(context, pageId, writer)
            }

            writer.write("</xournal>\n")
        }

        saveAsXopp(context, tempFile, fileName)
    }

    /**
     * Exports an entire book as a `.xopp` file.
     *
     * This method processes each page separately, writing the XML data
     * to a temporary file to prevent excessive memory usage. After all
     * pages are processed, the file is compressed into a `.xopp` format.
     *
     * @param context The application context.
     * @param bookId The ID of the book to export.
     */
    fun exportPage(context: Context, pageId: String) {
        val tempFile = File(context.cacheDir, "exported_page.xml")

        BufferedWriter(
            OutputStreamWriter(
                FileOutputStream(tempFile),
                Charsets.UTF_8
            )
        ).use { writer ->
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            writer.write("<xournal creator=\"Notable ${BuildConfig.VERSION_NAME}\" version=\"0.4\">\n")
            writePage(context, pageId, writer)
            writer.write("</xournal>\n")
        }

        saveAsXopp(context, tempFile, "exported_page")
    }


    /**
     * Writes a single page's XML data to the output stream.
     *
     * This method retrieves the strokes and images for the given page
     * and writes them to the provided BufferedWriter.
     *
     * @param context The application context.
     * @param pageId The ID of the page to process.
     * @param writer The BufferedWriter to write XML data to.
     */
    private fun writePage(context: Context, pageId: String, writer: BufferedWriter) {
        val pages = PageRepository(context)
        val (page, strokes) = pages.getWithStrokeById(pageId)
        val (_, images) = pages.getWithImageById(pageId)

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

        val root = doc.createElement("page")
        val strokeHeight = if (strokes.isEmpty()) 0 else strokes.maxOf(Stroke::bottom).toInt() + 50
        val scaleFactor = A4_WIDTH.toFloat() / SCREEN_WIDTH
        val height = strokeHeight.coerceAtLeast(SCREEN_HEIGHT) * scaleFactor

        root.setAttribute("width", A4_WIDTH.toString())
        root.setAttribute("height", height.toString())
        doc.appendChild(root)

        val bcgElement = doc.createElement("background")
        bcgElement.setAttribute("type", "solid")
        bcgElement.setAttribute("color", "#ffffffff")
        bcgElement.setAttribute("style", "plain")
        root.appendChild(bcgElement)


        val layer = doc.createElement("layer")
        root.appendChild(layer)



        for (stroke in strokes) {
            val strokeElement = doc.createElement("stroke")
            strokeElement.setAttribute("tool", stroke.pen.toString())
            strokeElement.setAttribute("color", getColorName(Color(stroke.color)))
            strokeElement.setAttribute("width", (stroke.size * scaleFactor).toString())

            val pointsString =
                stroke.points.joinToString(" ") { "${it.x * scaleFactor} ${it.y * scaleFactor}" }
            strokeElement.textContent = pointsString
            layer.appendChild(strokeElement)
        }

        for (image in images) {
            val imgElement = doc.createElement("image")

            val left = image.x * scaleFactor
            val top = image.y * scaleFactor
            val right = (image.x + image.width) * scaleFactor
            val bottom = (image.y + image.height) * scaleFactor

            imgElement.setAttribute("left", left.toString())
            imgElement.setAttribute("top", top.toString())
            imgElement.setAttribute("right", right.toString())
            imgElement.setAttribute("bottom", bottom.toString())

            image.uri?.let { uri ->
                imgElement.setAttribute("filename", uri)
                imgElement.textContent = convertImageToBase64(image.uri, context)
            }

            layer.appendChild(imgElement)
        }

        val xmlString = convertXmlToString(doc)
        writer.write(xmlString)
    }

    private fun convertImageToBase64(uri: String, context: Context): String {
        val inputStream = context.contentResolver.openInputStream(Uri.parse(uri))
        val bytes = inputStream?.readBytes() ?: return ""
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }


    /**
     * Converts an XML Document to a formatted string without the XML declaration.
     *
     * This is used to convert an individual page's XML structure into a string
     * before writing it to the output file. The XML declaration is removed to
     * prevent duplicate headers when merging pages.
     *
     * @param document The XML Document to convert.
     * @return The formatted XML string without the XML declaration.
     */
    private fun convertXmlToString(document: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes") // ❗ Omit XML header
        val writer = StringWriter()
        transformer.transform(DOMSource(document), StreamResult(writer))
        return writer.toString().trim() // Remove extra spaces or newlines
    }


    /**
     * Saves a temporary XML file as a compressed `.xopp` file.
     *
     * @param context The application context.
     * @param file The temporary XML file to compress.
     * @param fileName The name of the final `.xopp` file.
     */
    private fun saveAsXopp(context: Context, file: File, fileName: String) {
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

    /**
     * Maps a Compose Color to an Xournal++ color name.
     *
     * @param color The Compose Color object.
     * @return The corresponding color name as a string.
     */
    private fun getColorName(color: Color): String {
        return when (color) {
            Color.Black -> "black"
            Color.Blue -> "blue"
            Color.Red -> "red"
            Color.Green -> "green"
            Color.Magenta -> "magenta"
            Color.Yellow -> "yellow"
//            Color.DarkGray, Color.Gray -> "gray"
//            Color.Cyan -> "lightblue"
            else -> "#${Integer.toHexString(color.toArgb()).padStart(8, '0')}"
        }
    }
}