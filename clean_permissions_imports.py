with open('./app/src/main/java/com/example/PermissionsScreen.kt', 'r') as f:
    text = f.read()

# Fix package statement
if "package com.example" in text:
    parts = text.split("package com.example", 1)
    rest = parts[1]
    # format imports
    rest = rest.replace("import ", "\nimport ")
    text = "package com.example\n" + rest

with open('./app/src/main/java/com/example/PermissionsScreen.kt', 'w') as f:
    f.write(text)

print("Imports cleaned up")
