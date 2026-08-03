import re

with open("app/src/main/java/com/example/AIAssistantScreen.kt", "r") as f:
    text = f.read()

text = text.replace(
"""        coroutineScope.launch {
            delay(1500)
            val reply = "This is a simulated response. The actual API integration will be managed in the admin panel."
            messages = messages + ChatMessage(text = reply, isUser = false)
            isTyping = false
        }""",
"""        coroutineScope.launch {
            delay(1500)
            val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
            val apiKey = prefs.getString("gemini_api_key", "") ?: ""
            val reply = if (apiKey.isNotBlank()) {
                "API Key is configured! Ready to send real AI requests."
            } else {
                "This is a simulated response. Please configure the Gemini API key in the admin panel."
            }
            messages = messages + ChatMessage(text = reply, isUser = false)
            isTyping = false
        }""")

with open("app/src/main/java/com/example/AIAssistantScreen.kt", "w") as f:
    f.write(text)
