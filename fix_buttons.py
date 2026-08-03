with open('app/src/main/java/com/example/GestureStudioScreen.kt', 'r') as f:
    content = f.read()

# Make sure buttons have Color.White
old_btn = 'Text("Connect", fontSize = 12.sp)'
new_btn = 'Text("Connect", fontSize = 12.sp, color = Color.White)'
content = content.replace(old_btn, new_btn)

old_btn2 = 'Text("Click to Connect", fontSize = 12.sp)'
new_btn2 = 'Text("Click to Connect", fontSize = 12.sp, color = Color.White)'
content = content.replace(old_btn2, new_btn2)

old_btn3 = 'Text("Connecting", fontSize = 12.sp)'
new_btn3 = 'Text("Connecting", fontSize = 12.sp, color = Color.White)'
content = content.replace(old_btn3, new_btn3)

with open('app/src/main/java/com/example/GestureStudioScreen.kt', 'w') as f:
    f.write(content)

