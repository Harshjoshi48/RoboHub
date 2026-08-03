with open('app/src/main/java/com/example/GestureStudioScreen.kt', 'r') as f:
    content = f.read()

old_colors = """                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryBlue,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedTextColor = darkText,
                    unfocusedTextColor = darkText,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )"""

new_colors = """                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryBlue,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = darkText,
                    unfocusedTextColor = darkText,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )"""

content = content.replace(old_colors, new_colors)

with open('app/src/main/java/com/example/GestureStudioScreen.kt', 'w') as f:
    f.write(content)
