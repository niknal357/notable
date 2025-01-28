package com.olup.notable.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.olup.notable.modals.ActionButton


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