import re

with open('./app/src/main/java/com/example/PermissionsScreen.kt', 'r') as f:
    code = f.read()

# Put newline before every 'import ' or 'import'
code = re.sub(r'import\s*', '\nimport ', code)
code = re.sub(r'package com\.example\s*', 'package com.example\n\n', code)
code = re.sub(r'@Composable\s*', '\n\n@Composable\n', code)

with open('./app/src/main/java/com/example/PermissionsScreen.kt', 'w') as f:
    f.write(code)

print("Regex import split completed")
