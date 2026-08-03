import re

with open("app/src/main/java/com/example/HandControlScreen.kt", "r") as f:
    text = f.read()

start_idx = text.find("  val isDark = androidx.compose.foundation.isSystemInDarkTheme()")

new_colors = """  // Metallic dark grey for the fill to look like 3D render
  val fillColor = Color(0xFF2C313C)
  // Neon cyan for the wireframe/mesh
  val wireColor = Color(0xFF00E5FF) 
  val activeTipColor = Color(0xFFFF0055) // Neon pink for active tips
"""

end_idx = text.find("  Canvas(modifier = modifier)", start_idx)

if start_idx != -1 and end_idx != -1:
    with open("app/src/main/java/com/example/HandControlScreen.kt", "w") as f:
        f.write(text[:start_idx] + new_colors + text[end_idx:])
    print("Patched RoboticHandCanvas colors to neon")
else:
    print("Could not find color declarations")
