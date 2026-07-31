with open('./app/src/main/java/com/example/DashboardScreen.kt', 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "https://images.unsplash.com/photo-1581092335397-9583eb92d232" in line:
        if i < 250: # Hero Card
            lines[i] = line.replace("https://images.unsplash.com/photo-1581092335397-9583eb92d232?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80", "https://images.unsplash.com/photo-1614064016629-873b1849495e?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80")
        else: # Prosthetic Status Card
            lines[i] = line.replace("https://images.unsplash.com/photo-1581092335397-9583eb92d232?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80", "https://images.unsplash.com/photo-1620712943543-bcc4688e7485?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80")

with open('./app/src/main/java/com/example/DashboardScreen.kt', 'w') as f:
    f.writelines(lines)
