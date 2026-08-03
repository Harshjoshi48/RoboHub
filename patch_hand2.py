import re

with open("app/src/main/java/com/example/HandControlScreen.kt", "r") as f:
    text = f.read()

start_idx = text.find("@Composable\nfun RoboticHandCanvas(")

new_canvas = """
// 3D Point helper class for transformations
data class Point3D(val x: Float, val y: Float, val z: Float) {
    fun rotateX(angle: Float): Point3D {
        val c = kotlin.math.cos(angle.toDouble()).toFloat()
        val s = kotlin.math.sin(angle.toDouble()).toFloat()
        return Point3D(x, y * c - z * s, y * s + z * c)
    }
    fun rotateY(angle: Float): Point3D {
        val c = kotlin.math.cos(angle.toDouble()).toFloat()
        val s = kotlin.math.sin(angle.toDouble()).toFloat()
        return Point3D(x * c + z * s, y, -x * s + z * c)
    }
    fun rotateZ(angle: Float): Point3D {
        val c = kotlin.math.cos(angle.toDouble()).toFloat()
        val s = kotlin.math.sin(angle.toDouble()).toFloat()
        return Point3D(x * c - y * s, x * s + y * c, z)
    }
    fun add(p: Point3D) = Point3D(x + p.x, y + p.y, z + p.z)
    
    fun project(cx: Float, cy: Float): Pair<Offset, Float> {
        val cameraZ = 500f
        val scale = 1.6f
        val f = cameraZ / (cameraZ - z)
        return Pair(Offset(cx + x * f * scale, cy + y * f * scale), f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFinger(
    base: Point3D, lengths: List<Float>, splayZ: Float, curlX: Float, baseRadius: Float, 
    cx: Float, cy: Float, fillColor: Color, wireColor: Color, activeTipColor: Color, isThumb: Boolean = false
) {
    var currentPos = base
    
    val bend1 = if (isThumb) curlX * 0.3f else curlX * 0.5f
    val bend2 = if (isThumb) curlX * 0.4f else curlX * 0.6f
    val bend3 = if (isThumb) curlX * 0.3f else curlX * 0.4f
    
    var currentLocalAngleX = 0f
    val bends = listOf(bend1, bend2, bend3)
    
    var (prevP, prevF) = currentPos.project(cx, cy)
    var currentRadius = baseRadius
    
    for (i in 0 until 3) {
        currentLocalAngleX += bends[i]
        
        var v = Point3D(0f, -lengths[i], 0f)
        
        if (isThumb) {
            v = v.rotateX(kotlin.math.Math.toRadians(-currentLocalAngleX.toDouble()).toFloat())
            // Thumb sweeps across palm when closing
            v = v.rotateY(kotlin.math.Math.toRadians((currentLocalAngleX * 0.7f).toDouble()).toFloat())
        } else {
            v = v.rotateX(kotlin.math.Math.toRadians(-currentLocalAngleX.toDouble()).toFloat())
        }
        
        v = v.rotateZ(kotlin.math.Math.toRadians(splayZ.toDouble()).toFloat())
        
        currentPos = currentPos.add(v)
        val (nextP, nextF) = currentPos.project(cx, cy)
        val nextRadius = currentRadius * 0.8f
        
        val r1 = currentRadius * prevF
        val r2 = nextRadius * nextF
        
        val dx = nextP.x - prevP.x
        val dy = nextP.y - prevP.y
        val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        
        if (dist > 0.1f) {
            val nx = -dy / dist
            val ny = dx / dist
            
            val segPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(prevP.x + nx * r1, prevP.y + ny * r1)
                lineTo(nextP.x + nx * r2, nextP.y + ny * r2)
                lineTo(nextP.x - nx * r2, nextP.y - ny * r2)
                lineTo(prevP.x - nx * r1, prevP.y - ny * r1)
                close()
            }
            drawPath(segPath, color = fillColor)
            drawPath(segPath, color = wireColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
            
            // Wireframe center axis
            drawLine(wireColor, prevP, nextP, strokeWidth = 1f)
        }
        
        // Draw Joint (sphere projection)
        drawCircle(fillColor, r1, prevP)
        drawCircle(wireColor, r1, prevP, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
        drawArc(wireColor, 0f, 180f, false, topLeft = Offset(prevP.x - r1, prevP.y - r1*0.5f), size = Size(r1*2f, r1), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
        
        prevP = nextP
        prevF = nextF
        currentRadius = nextRadius
        
        // Draw illuminated fingertip
        if (i == 2) {
            drawCircle(fillColor, r2, nextP)
            drawCircle(wireColor, r2, nextP, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
            drawCircle(activeTipColor, r2 * 0.6f, nextP) 
        }
    }
}

@Composable
fun RoboticHandCanvas(
  thumbAngle: Int,
  indexAngle: Int,
  middleAngle: Int,
  ringAngle: Int,
  littleAngle: Int,
  modifier: Modifier = Modifier
) {
  val animThumb by animateFloatAsState(targetValue = thumbAngle.toFloat(), animationSpec = tween(600), label = "thumb")
  val animIndex by animateFloatAsState(targetValue = indexAngle.toFloat(), animationSpec = tween(600), label = "index")
  val animMiddle by animateFloatAsState(targetValue = middleAngle.toFloat(), animationSpec = tween(600), label = "middle")
  val animRing by animateFloatAsState(targetValue = ringAngle.toFloat(), animationSpec = tween(600), label = "ring")
  val animLittle by animateFloatAsState(targetValue = littleAngle.toFloat(), animationSpec = tween(600), label = "little")

  val fillColor = Color(0xFFF8FAFC) // Sleek white/light grey
  val wireColor = Color(0xFF64748B) // Slate grey for wireframe mesh
  val activeTipColor = Color(0xFF3B82F6) // Bright blue for tips

  Canvas(modifier = modifier) {
      val cx = size.width / 2f
      val cy = size.height * 0.75f // Move base down

      val wristL = Point3D(-35f, 60f, -10f)
      val wristR = Point3D(35f, 60f, -10f)
      val knuckleI = Point3D(-38f, -40f, 0f)
      val knuckleM = Point3D(-14f, -48f, 0f)
      val knuckleR = Point3D(14f, -42f, 0f)
      val knuckleL = Point3D(38f, -30f, 0f)
      val thumbBase = Point3D(-45f, 15f, 5f)

      val (pWL, fWL) = wristL.project(cx, cy)
      val (pWR, fWR) = wristR.project(cx, cy)
      val (pKI, fKI) = knuckleI.project(cx, cy)
      val (pKM, fKM) = knuckleM.project(cx, cy)
      val (pKR, fKR) = knuckleR.project(cx, cy)
      val (pKL, fKL) = knuckleL.project(cx, cy)
      val (pTB, fTB) = thumbBase.project(cx, cy)

      // Draw Palm Base
      val palmPath = androidx.compose.ui.graphics.Path().apply {
          moveTo(pWL.x, pWL.y)
          lineTo(pTB.x, pTB.y)
          lineTo(pKI.x, pKI.y)
          lineTo(pKM.x, pKM.y)
          lineTo(pKR.x, pKR.y)
          lineTo(pKL.x, pKL.y)
          lineTo(pWR.x, pWR.y)
          close()
      }
      
      drawPath(palmPath, color = fillColor)
      drawPath(palmPath, color = wireColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
      
      // Wireframe grid lines for palm
      drawLine(wireColor, pTB, pKM, strokeWidth = 1f)
      drawLine(wireColor, pTB, pKR, strokeWidth = 1f)
      drawLine(wireColor, pWL, pKL, strokeWidth = 1f)
      drawLine(wireColor, pWL, pKM, strokeWidth = 1f)
      drawLine(wireColor, pWR, pKI, strokeWidth = 1f)

      // Draw all fingers in back-to-front projection sequence
      drawFinger(knuckleL, listOf(30f, 20f, 15f), 15f, animLittle, 9f, cx, cy, fillColor, wireColor, activeTipColor)
      drawFinger(knuckleR, listOf(40f, 25f, 20f), 5f, animRing, 10f, cx, cy, fillColor, wireColor, activeTipColor)
      drawFinger(knuckleM, listOf(45f, 30f, 22f), -2f, animMiddle, 11f, cx, cy, fillColor, wireColor, activeTipColor)
      drawFinger(knuckleI, listOf(40f, 25f, 20f), -10f, animIndex, 10f, cx, cy, fillColor, wireColor, activeTipColor)
      drawFinger(thumbBase, listOf(35f, 25f, 20f), -45f, animThumb, 12f, cx, cy, fillColor, wireColor, activeTipColor, isThumb = true)
  }
}
"""

if start_idx != -1:
    with open("app/src/main/java/com/example/HandControlScreen.kt", "w") as f:
        f.write(text[:start_idx] + new_canvas)
    print("Patched RoboticHandCanvas to fix local classes")
else:
    print("Could not find RoboticHandCanvas function")
