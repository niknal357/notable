package com.ethran.notable.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.ethran.notable.R
import com.ethran.notable.db.BackgroundType
import com.ethran.notable.utils.ensureBackgroundsFolder
import com.ethran.notable.utils.noRippleClickable
import java.io.File

@Composable
fun <T> SelectMenu(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }

    Box {
        Row {
            Text(
                text = options.find { it.first == value }?.second ?: "Undefined",
                fontWeight = FontWeight.Light,
                modifier = Modifier.noRippleClickable { isExpanded = true })

            Icon(
                Icons.Rounded.ArrowDropDown, contentDescription = "open select",
                modifier = Modifier.height(20.dp)
            )
        }
        if (isExpanded) Popup(onDismissRequest = { isExpanded = false }) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .border(0.5.dp, Color.Black, RectangleShape)
                    .background(Color.White)
            ) {
                options.map {
                    Text(
                        text = it.second,
                        fontWeight = FontWeight.Light,
                        color = if (it.first == value) Color.White else Color.Black,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (it.first == value) Color.Black else Color.White)
                            .padding(20.dp, 10.dp)
                            .noRippleClickable {
                                onChange(it.first)
                                isExpanded = false
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun BackgroundSelector(
    currentBackground: String,
    currentBackgroundType: BackgroundType,
    onBackgroundChange: (String, BackgroundType) -> Unit,
    onRequestFilePicker: () -> Unit,
    maxPages: Int? = null,
    currentPage: Int? = null
) {
    Column {
        when (currentBackgroundType) {
            is BackgroundType.Pdf -> {
                Text("Choose PDF background", fontWeight = FontWeight.Bold)

                val folderName = currentBackgroundType.folderName
                val folder = File(ensureBackgroundsFolder(), folderName)

                val uriOptions = folder.listFiles()
                    ?.filter { it.isFile }
                    ?.map { file ->
                        Pair(file.absolutePath, file.nameWithoutExtension)
                    } ?: emptyList()
                val pdfOptions = listOf(
                    "file" to "Import PDF"
                ) + uriOptions

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .padding(8.dp)
                ) {
                    items(pdfOptions) { (value, label) ->
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .border(
                                    width = if (value == currentBackground) 3.dp else 1.dp,
                                    color = if (value == currentBackground) Color.Black else Color.LightGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (value == "file") {
                                        onRequestFilePicker()
                                    } else {
                                        onBackgroundChange(value, currentBackgroundType)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .background(Color.Gray)
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "PDF",
                                    tint = Color.White,
                                    modifier = Modifier.height(36.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(label, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (currentBackground.endsWith(".pdf") && maxPages != null && currentPage != null) {
                    Spacer(Modifier.height(10.dp))
                    Text("Select PDF Page", fontWeight = FontWeight.SemiBold)

                    var pageText by remember {
                        mutableStateOf(
                            (currentPage + 1).toString()
                        )
                    }
                    val pageNumber = pageText.toIntOrNull() ?: 1

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 100.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (pageNumber > 1) {
                                    val newPage = (pageNumber - 1).coerceAtMost(maxPages)
                                    pageText = newPage.toString()
                                    onBackgroundChange(
                                        currentBackground,
                                        BackgroundType.Pdf(newPage - 1)
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Previous Page")
                        }

                        OutlinedTextField(
                            value = pageText,
                            onValueChange = {
                                val input = it.toIntOrNull()
                                if (input != null && input in 1..maxPages) {
                                    pageText = input.toString()
                                    onBackgroundChange(
                                        currentBackground,
                                        BackgroundType.Pdf(input - 1)
                                    )
                                } else {
                                    pageText = it
                                }
                            },
                            label = { Text("Page number") },
                            keyboardOptions = KeyboardOptions.Default.copy(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                if (pageNumber < maxPages) {
                                    val newPage = (pageNumber + 1).coerceAtLeast(1)
                                    pageText = newPage.toString()
                                    onBackgroundChange(
                                        currentBackground,
                                        BackgroundType.Pdf(newPage - 1)
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Next Page")
                        }

                    }

                    Text(
                        "PDF has $maxPages pages",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .align(Alignment.CenterHorizontally)
                    )

                }


            }


            BackgroundType.CoverImage, BackgroundType.Image, BackgroundType.ImageRepeating -> {
                Text("Choose Cover Image", fontWeight = FontWeight.Bold)

                val baseOptions = listOf(
                    Triple("iris", "Iris", painterResource(id = R.drawable.iris)),
                )

                val folderName = currentBackgroundType.folderName
                val folder = File(ensureBackgroundsFolder(), folderName)

                val uriOptions = folder.listFiles()
                    ?.filter { it.isFile }
                    ?.map { file ->
                        Triple(file.absolutePath, file.nameWithoutExtension, null as Painter?)
                    } ?: emptyList()

                val chooseFileOption = listOf(Triple("file", "Choose From File...", null))

                val options = baseOptions + uriOptions + chooseFileOption

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .padding(8.dp)
                ) {
                    items(options) { (value, label, painter) ->
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .border(
                                    width = if (value == currentBackground) 3.dp else 1.dp,
                                    color = if (value == currentBackground) Color.Black else Color.LightGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (value == "file") {
                                        onRequestFilePicker()
                                    } else {
                                        onBackgroundChange(value, currentBackgroundType)
                                    }
                                }
                        ) {
                            if (painter != null) {
                                Image(
                                    painter = painter,
                                    contentDescription = label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                )
                            } else if (value != "file") {
                                val bitmap = remember(value) {
                                    BitmapFactory.decodeFile(value)?.asImageBitmap()
                                }

                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .background(Color.Gray),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            label,
                                            color = Color.White,
                                            fontWeight = FontWeight.Light
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .background(Color.Gray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        color = Color.White,
                                        fontWeight = FontWeight.Light
                                    )
                                }
                            }
                        }
                    }
                }
            }

            BackgroundType.Native -> {
                Text("Choose Native Template", fontWeight = FontWeight.Bold)

                val nativeOptions = listOf(
                    "blank" to "Blank page",
                    "dotted" to "Dot grid",
                    "lined" to "Lines",
                    "squared" to "Small squares grid",
                    "hexed" to "Hexagon grid"
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .padding(8.dp)
                ) {
                    items(nativeOptions) { (value, label) ->
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .border(
                                    width = if (value == currentBackground) 3.dp else 1.dp,
                                    color = if (value == currentBackground) Color.Black else Color.LightGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onBackgroundChange(value, currentBackgroundType) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
    }
}
