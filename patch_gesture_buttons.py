with open('app/src/main/java/com/example/GestureStudioScreen.kt', 'r') as f:
    content = f.read()

# Add Color.White to Create New Gesture button text
old_btn1 = 'Text("+ Create New Gesture", fontSize = 16.sp, fontWeight = FontWeight.Bold)'
new_btn1 = 'Text("+ Create New Gesture", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)'
content = content.replace(old_btn1, new_btn1)

# Add Color.White to Save Gesture button text
old_btn2 = 'Text("Save Gesture", fontSize = 16.sp, fontWeight = FontWeight.Bold)'
new_btn2 = 'Text("Save Gesture", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)'
content = content.replace(old_btn2, new_btn2)

with open('app/src/main/java/com/example/GestureStudioScreen.kt', 'w') as f:
    f.write(content)
