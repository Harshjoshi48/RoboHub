with open('app/src/main/java/com/example/GestureStudioScreen.kt', 'r') as f:
    content = f.read()

# Fix the Toast
old_toast = 'Toast.makeText(context, "Phela ESP32 connect karo", Toast.LENGTH_SHORT).show()'
new_toast = 'Toast.makeText(context, "ESP32 is not connected", Toast.LENGTH_SHORT).show()'
content = content.replace(old_toast, new_toast)

# Fix the TopAppBar in CreateEditGestureScreen
old_top_bar = """        topBar = {
            TopAppBar(
                title = { Text(if (gesture == null) "Create Gesture" else "Edit Gesture", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = lightBg)
            )
        },"""

new_top_bar = """        topBar = {
            TopAppBar(
                title = { Text(if (gesture == null) "Create Gesture" else "Edit Gesture", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = darkText) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = darkText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = lightBg)
            )
        },"""
content = content.replace(old_top_bar, new_top_bar)

# Fix the OutlinedTextField for Name
old_name_field = """            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Gesture Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )"""

new_name_field = """            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Gesture Name", color = Color(0xFF64748B)) },
                textStyle = androidx.compose.ui.text.TextStyle(color = darkText, fontSize = 16.sp),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryBlue,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedTextColor = darkText,
                    unfocusedTextColor = darkText,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                singleLine = true
            )"""
content = content.replace(old_name_field, new_name_field)

# Fix the OutlinedTextField for Delay
old_delay_field = """            OutlinedTextField(
                value = delayStr,
                onValueChange = { delayStr = it },
                label = { Text("Speed/Delay (ms)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )"""

new_delay_field = """            OutlinedTextField(
                value = delayStr,
                onValueChange = { delayStr = it },
                label = { Text("Speed/Delay (ms)", color = Color(0xFF64748B)) },
                textStyle = androidx.compose.ui.text.TextStyle(color = darkText, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryBlue,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedTextColor = darkText,
                    unfocusedTextColor = darkText,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                singleLine = true
            )"""
content = content.replace(old_delay_field, new_delay_field)

with open('app/src/main/java/com/example/GestureStudioScreen.kt', 'w') as f:
    f.write(content)

