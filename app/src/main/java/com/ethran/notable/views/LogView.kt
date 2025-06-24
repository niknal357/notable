package com.ethran.notable.views

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.ethran.notable.BuildConfig
import com.ethran.notable.classes.PageDataManager
import com.ethran.notable.utils.getDbDir
import com.onyx.android.sdk.utils.ClipboardUtils.copyToClipboard
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class ReportData {
    val deviceInfo: String = buildDeviceInfo()
    private val logs: String = getRecentLogs()

    companion object {
        private const val MAX_LOG_LINES = 40
        private const val LOG_LINE_REGEX =
            """^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}.\d{3})\s+(\d+)\s+(\d+)\s([VDIWE])\s([^:]+):\s(.*)$"""
    }

    private fun rapportMarkdown(includeLogs: Boolean, description: String): String {
        val formatedLogs = formatLogsForDisplay()
        return buildString {
            append("### Description\n")
            append(description).append("\n\n")
            append("### Device Info\n")
            append(deviceInfo.replace("•", "-")).append("\n")
            if (includeLogs) {
                append("\n### Diagnostic Logs\n```\n")
                append(formatedLogs)
                append("\n```")
            }
        }
    }

    private fun getTitle(description: String): String {
        return description.take(40)
    }

    private fun getRecentLogs(): String {
        return try {
            // Get logs with threadtime format (most detailed format)
            val process = Runtime.getRuntime().exec("logcat -d -v threadtime")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            // Read all lines and keep only the newest matching entries
            val allLines = reader.useLines { lines ->
                lines.filter { it.matches(Regex(LOG_LINE_REGEX)) }
                    .toList()
            }

            // Take the most recent logs and reverse order (newest first)
            val recentLines = allLines.takeLast(MAX_LOG_LINES).reversed()

            if (recentLines.isEmpty()) "No recent logs found"
            else recentLines.joinToString("\n")
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    fun formatLogsForDisplay(): String {
        return logs.lines().joinToString("\n") { line ->
            val match = Regex(LOG_LINE_REGEX).find(line)
            if (match != null) {
                val (datetime, _, _, level, tag, message) = match.destructured
                val flag = when (level) {
                    "E" -> "🔴"
                    "W" -> "🟠"
                    "I" -> "🔵"
                    "D" -> "🟢"
                    "V" -> "⚪"
                    else -> "⚫"
                }
                "$flag $datetime $level $tag: $message"
            } else {
                line // Fallback for non-matching lines
            }
        }
    }

    private fun buildDeviceInfo(): String {
        val usedHeapMB = Runtime.getRuntime().totalMemory() / 1024 / 1024
        val maxHeapMB = Runtime.getRuntime().maxMemory() / 1024 / 1024
        val appMemory = getMemoryUsedByApp()
        val diskUsage = getDiscSpaceUsage()

        return """
        |• Model: ${Build.MANUFACTURER} ${Build.MODEL}
        |• Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
        |• App Version: ${BuildConfig.VERSION_NAME}
        |• Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}
        |• Available RAM: $usedHeapMB / $maxHeapMB MB
        |• App Memory(PageData/AllMemory): ${PageDataManager.getUsedMemory()}/$appMemory
        |• Disk Usage: $diskUsage
    """.trimMargin()
    }

    private fun getDiscSpaceUsage(): String {
        return try {
            val dbDir = getDbDir()
            val usedBytes = getFolderSize(dbDir)
            val usedMB = usedBytes / (1024 * 1024) // Convert to MB
            "$usedMB MB"
        } catch (e: Exception) {
            "Unavailable"
        }
    }

    private fun getFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return total
    }

    private fun getMemoryUsedByApp(): String {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 // MB
            "$usedMem MB"
        } catch (e: Exception) {
            "Unavailable"
        }
    }


    fun copyReportToClipboard(context: Context, description: String, includeLogs: Boolean) {
        copyToClipboard(
            context,
            rapportMarkdown(includeLogs, description.ifBlank { "_No description provided_" })
        )
        Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun submitBugReport(context: Context, description: String, includeLogs: Boolean) {
        try {
            val url = "https://github.com/ethran/notable/issues/new?" +
                    "title=${URLEncoder.encode("Bug: ${getTitle(description)}", "UTF-8")}" +
                    "&body=${URLEncoder.encode(rapportMarkdown(includeLogs, description), "UTF-8")}"

            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to submit report", Toast.LENGTH_LONG).show()
        }
    }
}


@Composable
fun BugReportScreen(navController: NavController) {
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    var includeLogs by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current
    val reportData = ReportData()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .fillMaxHeight()
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text("Report an Issue", style = MaterialTheme.typography.h6)
        }

        Spacer(Modifier.height(16.dp))

        // Description field
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Issue description") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        Spacer(Modifier.height(16.dp))

        // Report Preview Card
        ReportPreviewCard(reportData, description.ifBlank { "_No description provided_" }, includeLogs)

        Spacer(Modifier.height(16.dp))

        // Include logs toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = includeLogs,
                onCheckedChange = { includeLogs = it }
            )
            Spacer(Modifier.width(8.dp))
            Text("Include diagnostic logs")
        }

        Spacer(Modifier.height(16.dp))

        // Action buttons
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(
                onClick = { reportData.copyReportToClipboard(context, description, includeLogs) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Copy Report")
            }

            Spacer(Modifier.width(16.dp))

            Button(
                onClick = {
                    reportData.submitBugReport(context, description, includeLogs)
                },
                modifier = Modifier.weight(1f),
                enabled = description.isNotBlank()
            ) {
                Text("Submit via GitHub")
            }
        }
    }
}
@Composable
private fun ReportPreviewCard(
    reportData: ReportData,
    description: String,
    includeLogs: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f),
        elevation = 4.dp,
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            Text("📋 Report Preview", style = MaterialTheme.typography.subtitle1)
            Spacer(Modifier.height(8.dp))

            // Description
            Text(
                "📝 Description:",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary
            )
            Text(description, modifier = Modifier.padding(vertical = 4.dp))

            // Device Info
            Text(
                "📱 Device Info:",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary
            )
            Text(reportData.deviceInfo, modifier = Modifier.padding(vertical = 4.dp))

            // Logs - only this section should be scrollable
            if (includeLogs) {
                Text(
                    "📋 Logs:",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
                Box(
                    modifier = Modifier
                        .weight(1f) // Take remaining space
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        reportData.formatLogsForDisplay(),
                        modifier = Modifier.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.body2.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }
    }
}