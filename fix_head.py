with open('./app/src/main/java/com/example/PermissionsScreen.kt', 'r') as f:
    text = f.read()

text = text.replace("package com.exampleimport", "package com.example\n\nimport")
text = text.replace("import ", "\nimport ")

with open('./app/src/main/java/com/example/PermissionsScreen.kt', 'w') as f:
    f.write(text)

print("Head fixed")
