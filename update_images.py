with open('./app/src/main/java/com/example/DashboardScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('"https://images.unsplash.com/photo-1614064016629-873b1849495e?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80"', '"https://images.unsplash.com/photo-1589254065878-42c9da997008?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80"')
content = content.replace('"https://images.unsplash.com/photo-1620712943543-bcc4688e7485?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80"', '"https://images.unsplash.com/photo-1535378917042-10a22c95931a?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80"')

with open('./app/src/main/java/com/example/DashboardScreen.kt', 'w') as f:
    f.write(content)
