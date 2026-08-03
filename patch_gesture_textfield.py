with open('app/src/main/java/com/example/GestureStudioScreen.kt', 'r') as f:
    content = f.read()

# Fix name textfield
old_name = """            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Gesture Name", color = Color(0xFF64748B)) },
                textStyle = androidx.compose.ui.text.TextStyle(color = darkText, fontSize = 16.sp),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryBlue,
                    unfocusedBorderColor = Color.Black,
                    focusedTextColor = darkText,
                    unfocusedTextColor = darkText,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                singleLine = true
            )"""

new_name = """            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Gesture Name", color = Color(0xFF64748B)) },
                textStyle = androidx.compose.ui.text.TextStyle(color = darkText, fontSize = 16.sp),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF94A3B8), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = darkText,
                    unfocusedTextColor = darkText,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                singleLine = true
            )"""
content = content.replace(old_name, new_name)

old_delay = """            OutlinedTextField(
                value = delayStr,
                onValueChange = { delayStr = it },
                label = { Text("Speed/Delay (ms)", color = Color(0xFF64748B)) },
                textStyle = androidx.compose.ui.text.TextStyle(color = darkText, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryBlue,
                    unfocusedBorderColor = Color.Black,
                    focusedTextColor = darkText,
                    unfocusedTextColor = darkText,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                singleLine = true
            )"""

new_delay = """            OutlinedTextField(
                value = delayStr,
                onValueChange = { delayStr = it },
                label = { Text("Speed/Delay (ms)", color = Color(0xFF64748B)) },
                textStyle = androidx.compose.ui.text.TextStyle(color = darkText, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF94A3B8), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = darkText,
                    unfocusedTextColor = darkText,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                singleLine = true
            )"""
content = content.replace(old_delay, new_delay)

with open('app/src/main/java/com/example/GestureStudioScreen.kt', 'w') as f:
    f.write(content)
