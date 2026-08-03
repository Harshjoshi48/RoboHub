with open('.env.example', 'r') as f:
    content = f.read()

content = content.replace("GEMINI_API_KEY=MY_GEMINI_API_KEY", "# GEMINI_API_KEY=MY_GEMINI_API_KEY")

with open('.env.example', 'w') as f:
    f.write(content)
