package com.olup.notable.modals


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.olup.notable.AppRepository
import com.olup.notable.LocalSnackContext
import com.olup.notable.PagePreview
import com.olup.notable.SnackConf
import com.olup.notable.TAG
import com.olup.notable.components.SelectMenu
import com.olup.notable.db.BookRepository
import com.olup.notable.exportBook
import io.shipbook.shipbooksdk.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@ExperimentalComposeUiApi
@Composable
fun NotebookConfigDialog(bookId: String, onClose: () -> Unit) {
    val bookRepository = BookRepository(LocalContext.current)
    val book by bookRepository.getByIdLive(bookId).observeAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackManager = LocalSnackContext.current

    if (book == null) return

    var bookTitle by remember {
        mutableStateOf(book!!.title)
    }
    val formattedCreatedAt =
        remember { android.text.format.DateFormat.format("dd MMM yyyy HH:mm", book!!.createdAt) }
    val formattedUpdatedAt =
        remember { android.text.format.DateFormat.format("dd MMM yyyy HH:mm", book!!.updatedAt) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val appRepository = AppRepository(LocalContext.current)
    val folders by appRepository.folderRepository.getAllInFolder(book!!.parentFolderId)
        .observeAsState()
    val parentFolder = appRepository.folderRepository.getParent(book!!.parentFolderId)
    var showMoveDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            onClose()
        }
    ) {
        val focusManager = LocalFocusManager.current

        Column(
            modifier = Modifier
                .background(Color.White)
                .fillMaxWidth()
                .border(2.dp, Color.Black, RectangleShape)
                .padding(16.dp)
                .padding(top = 24.dp, bottom = 16.dp)
        ) {
            // Header Section
            Row(Modifier.padding(bottom = 16.dp)) {
                Box(
                    modifier = Modifier
                        .size(200.dp, 250.dp)
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    val pageId = book!!.pageIds[0]
                    PagePreview(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .border(1.dp, Color.Black, RectangleShape), pageId
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row {
                        Text(
                            text = "Title:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                        Spacer(Modifier.width(20.dp))
                        BasicTextField(
                            value = bookTitle,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Light,
                                fontSize = 24.sp
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            onValueChange = { bookTitle = it },
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                            }),
                            modifier = Modifier
                                .background(Color(230, 230, 230, 255))
                                .padding(10.dp, 0.dp)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused) {
                                        Log.i(TAG, "loose focus")
                                        val updatedBook = book!!.copy(title = bookTitle)
                                        bookRepository.update(updatedBook)
                                    }
                                }


                        )
                    }

                    Row{
                        Text(text = "Default Background Template")

                        Spacer(Modifier.width(30.dp))
                        SelectMenu(
                            options = listOf(
                                "blank" to "Blank page",
                                "dotted" to "Dot grid",
                                "lined" to "Lines",
                                "squared" to "Small squares grid"
                            ),
                            onChange = {
                                val updatedBook = book!!.copy(defaultNativeTemplate = it)
                                bookRepository.update(updatedBook)
                            },
                            // this once thrown null ptr exception, when deleting notebook.
                            value = book!!.defaultNativeTemplate
                        )

                    }
                    Text("Pages: ${book!!.pageIds.size}")
                    Text("Size: TODO!")
                    Text("Created: $formattedCreatedAt")
                    Text("Last Updated: $formattedUpdatedAt")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Grid Actions Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ActionButton("Delete") {
                    showDeleteDialog = true
                }
                ActionButton("Move") {
                    showMoveDialog = true
                }
                ActionButton("Export") {
                    // TODO: Do not duplicate code from ToolbarMenu!
                    scope.launch {
                        val removeSnack =
                            snackManager.displaySnack(
                                SnackConf(
                                    text = "Exporting the book to PDF...",
                                    id = "exportSnack"
                                )
                            )
                        delay(10L) // Why do I need this ?

                        val message = exportBook(context, bookId)
                        removeSnack()
                        snackManager.displaySnack(
                            SnackConf(text = message, duration = 2000)
                        )
                    }
                    onClose()
                }
                ActionButton("Copy") {
                    scope.launch {
                        snackManager.displaySnack(
                            SnackConf(text = "Not implemented!", duration = 2000)
                        )
                    }
                }

            }
        }
        // Confirmation Dialog for Deletion
        if (showDeleteDialog) {
            ShowConfirmationDialog(
                title = "Confirm Deletion",
                message = "Are you sure you want to delete \"${book!!.title}\"?",
                onConfirm = {
                    bookRepository.delete(bookId)
                    showDeleteDialog = false
                    onClose()
                },
                onCancel = {
                    showDeleteDialog = false
                }
            )
        }
        // Folder Selection Dialog
        if (showMoveDialog) {
            ShowFolderSelectionDialog(
                notebookName = book!!.title,
                availableFolders = folders?.associate { it.title to it.id } ?: emptyMap(),
                back = parentFolder,
                onCancel = { showMoveDialog = false },
                onConfirm = { selectedFolder ->
                    Log.i(TAG, "folder:" + selectedFolder.toString())
                    val updatedBook = book!!.copy(parentFolderId = selectedFolder)
                    bookRepository.update(updatedBook)
                    showMoveDialog = false
                }
            )
        }
    }

}

@Composable
fun ActionButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(100.dp, 40.dp)
            .background(Color.LightGray, RectangleShape)
            .border(1.dp, Color.Black, RectangleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ShowConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = { onCancel() }) {
        Column(
            modifier = Modifier
                .background(Color.White)
                .border(1.dp, Color.Black, RectangleShape),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(text = message, fontSize = 16.sp)
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                ActionButton(text = "Cancel", onClick = onCancel)
                ActionButton(text = "Confirm", onClick = onConfirm)
            }
        }
    }
}

@Composable
fun ShowFolderSelectionDialog(
    notebookName: String,
    availableFolders: Map<String, String>,
    back: String?,
    onCancel: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var selectedFolder by remember { mutableStateOf(back) }

    Dialog(onDismissRequest = { onCancel() }) {
        Column(
            modifier = Modifier
                .background(Color.White)
                .border(1.dp, Color.Black, RectangleShape),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Choose folder to move \"$notebookName\":",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Folder List
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFolder = back }
                        .padding(8.dp)
                        .background(if (selectedFolder == back) Color.LightGray else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "..", fontSize = 16.sp, fontWeight = FontWeight.Normal)
                }
                availableFolders.forEach { (title, id) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFolder = id }
                            .padding(8.dp)
                            .background(if (selectedFolder == id) Color.LightGray else Color.Transparent),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Normal)
                    }
                }
            }

            // Cancel and Confirm Buttons
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
                ActionButton("Cancel", onClick = onCancel)
                ActionButton("Confirm", onClick = { onConfirm(selectedFolder) })
            }
        }
    }
}