import re

with open('./app/src/main/java/com/example/PermissionsScreen.kt', 'r') as f:
    text = f.read()

# Remove any leading imports before package com.example
if "package com.example" in text:
    idx = text.index("package com.example")
    text = text[idx:]

# Split imports and rest of file
# Replace all "import " with "\nimport "
body_idx = text.index("@Composable")
header = text[:body_idx]
body = text[body_idx:]

# Clean header
header = re.sub(r'import\s+', '\nimport ', header)
header = header.replace('package com.example', 'package com.example\n')

# Deduplicate header imports and lines
lines = [l.strip() for l in header.split('\n') if l.strip()]
clean_header = '\n'.join(lines) + '\n\n'

new_file = clean_header + body
with open('./app/src/main/java/com/example/PermissionsScreen.kt', 'w') as f:
    f.write(new_file)

print("PermissionsScreen header clean rewrite complete")
