package com.theblankstate.libri.view.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CreateShelfDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
    initialName: String = "",
    initialDescription: String = "",
    title: String = "Create Shelf",
    confirmButtonText: String = "Create"
) {
    var shelfName by remember { mutableStateOf(initialName) }
    var shelfDescription by remember { mutableStateOf(initialDescription) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = shelfName,
                    onValueChange = {
                        shelfName = it
                        showError = false
                    },
                    label = { Text("Shelf Name*") },
                    placeholder = { Text("e.g., Favorites, To Review") },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("Shelf name is required") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = shelfDescription,
                    onValueChange = { shelfDescription = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Add a description...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (shelfName.isBlank()) {
                        showError = true
                    } else {
                        onConfirm(shelfName.trim(), shelfDescription.trim())
                    }
                }
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
