with open('./app/src/main/java/com/example/PermissionsScreen.kt', 'r') as f:
    code = f.read()

# Make sure imports are separated by newlines
code = code.replace("package com.example", "package com.example\n\n")
code = code.replace("import ", "\nimport ")
code = code.replace("@Composable", "\n\n@Composable")

with open('./app/src/main/java/com/example/PermissionsScreen.kt', 'w') as f:
    f.write(code)

print("Formatted kotlin file")
