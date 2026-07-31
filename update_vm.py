with open('./app/src/main/java/com/example/DashboardViewModel.kt', 'r') as f:
    content = f.read()

new_states = """
    private val _responseTime = MutableStateFlow(0)
    val responseTime: StateFlow<Int> = _responseTime

    private val _servoTemp = MutableStateFlow(0f)
    val servoTemp: StateFlow<Float> = _servoTemp

    private val _gripForce = MutableStateFlow(0f)
    val gripForce: StateFlow<Float> = _gripForce

    private val _therapyProgress = MutableStateFlow(0.6f)
    val therapyProgress: StateFlow<Float> = _therapyProgress
"""

content = content.replace("    // Data lists for the charts", new_states + "\n    // Data lists for the charts")

reset_states = """        _responseTime.value = 0
        _servoTemp.value = 0f
        _gripForce.value = 0f
"""
content = content.replace("        _battery.value = 0\n", "        _battery.value = 0\n" + reset_states)

update_states = """                _responseTime.value = Random.nextInt(10, 25)
                _servoTemp.value = 35f + Random.nextFloat() * 3f
                _gripForce.value = 3f + Random.nextFloat() * 2f
                _therapyProgress.value = Math.min(1f, _therapyProgress.value + 0.0005f)
"""
content = content.replace("                _battery.value = if (Random.nextFloat() > 0.99f) Math.max(0, _battery.value - 1) else _battery.value\n", "                _battery.value = if (Random.nextFloat() > 0.99f) Math.max(0, _battery.value - 1) else _battery.value\n" + update_states)


with open('./app/src/main/java/com/example/DashboardViewModel.kt', 'w') as f:
    f.write(content)
