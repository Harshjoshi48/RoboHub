with open('./app/src/main/java/com/example/PermissionsScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("import androidx.compose.material.icons.outlined.*package com.example", "package com.example\nimport androidx.compose.material.icons.outlined.*")

with open('./app/src/main/java/com/example/PermissionsScreen.kt', 'w') as f:
    f.write(content)

print("Fixed PermissionsScreen package header")
