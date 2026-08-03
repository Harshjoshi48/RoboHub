package com.example

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandControlScreen(
  onBack: () -> Unit,
  onNavigateToHome: () -> Unit,
  onNavigateToAI: () -> Unit,
  onNavigateToReports: () -> Unit,
  onNavigateToProfile: () -> Unit,
  viewModel: DashboardViewModel
) {
  val context = LocalContext.current

  val primaryBlue = Color(0xFF2F80ED)
  val lightBg = Color(0xFFF8F9FA)
  val darkText = Color(0xFF1E293B)
  val textGray = Color(0xFF64748B)
  val borderGray = Color(0xFFE2E8F0)
  val redEmergency = Color(0xFFDC2626)
  val lightBlueBtn = Color(0xFFE8F0FE)

  val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
  val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
  val thumbAngle by viewModel.thumbAngle.collectAsStateWithLifecycle()
  val indexAngle by viewModel.indexAngle.collectAsStateWithLifecycle()
  val middleAngle by viewModel.middleAngle.collectAsStateWithLifecycle()
  val ringAngle by viewModel.ringAngle.collectAsStateWithLifecycle()
  val littleAngle by viewModel.littleAngle.collectAsStateWithLifecycle()
  val connectionMessage by viewModel.connectionMessage.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(connectionMessage) {
    connectionMessage?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearMessage()
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      NavigationBar(
        containerColor = Color.White,
        tonalElevation = 16.dp
      ) {
        NavigationBarItem(
          icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
          label = { Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Medium) },
          selected = false,
          onClick = onNavigateToHome,
          colors = NavigationBarItemDefaults.colors(
            unselectedIconColor = textGray,
            unselectedTextColor = textGray
          )
        )
        NavigationBarItem(
          icon = { Icon(Icons.Outlined.PanTool, contentDescription = "Control") },
          label = { Text("Control", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          selected = true,
          onClick = { },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = primaryBlue,
            selectedTextColor = primaryBlue,
            indicatorColor = primaryBlue.copy(alpha = 0.15f)
          )
        )
        NavigationBarItem(
          icon = { Icon(Icons.Outlined.Psychology, contentDescription = "AI") },
          label = { Text("AI", fontSize = 10.sp) },
          selected = false,
          onClick = onNavigateToAI,
          colors = NavigationBarItemDefaults.colors(
            unselectedIconColor = textGray,
            unselectedTextColor = textGray
          )
        )
        NavigationBarItem(
          icon = { Icon(Icons.Outlined.Timeline, contentDescription = "Progress") },
          label = { Text("Progress", fontSize = 10.sp) },
          selected = false,
          onClick = onNavigateToReports,
          colors = NavigationBarItemDefaults.colors(
            unselectedIconColor = textGray,
            unselectedTextColor = textGray
          )
        )
        NavigationBarItem(
          icon = { Icon(Icons.Outlined.Person, contentDescription = "Profile") },
          label = { Text("Profile", fontSize = 10.sp) },
          selected = false,
          onClick = onNavigateToProfile,
          colors = NavigationBarItemDefaults.colors(
            unselectedIconColor = textGray,
            unselectedTextColor = textGray
          )
        )
      }
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(lightBg)
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
      // Header Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.size(40.dp)
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = primaryBlue
          )
        }

        Text(
          text = "Hand Control",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = darkText
        )

        Surface(
          onClick = { viewModel.toggleConnection(context) },
          shape = RoundedCornerShape(20.dp),
          color = if (isConnected) Color(0xFFE6F4EA) else Color(0xFFFEF3C7),
          modifier = Modifier.clip(RoundedCornerShape(20.dp))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .background(
                  color = if (isConnected) Color(0xFF16A34A) else Color(0xFFD97706),
                  shape = CircleShape
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isConnecting) "Connecting..." else if (isConnected) "Connected" else "Wi-Fi Off",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = if (isConnected) Color(0xFF15803D) else Color(0xFFB45309)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Graphic Robotic Hand Visualizer Card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .height(350.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          contentAlignment = Alignment.Center
        ) {
          // Dynamic Canvas rendering the robotic hand servos and finger flex angles
          RoboticHandCanvas(
            thumbAngle = thumbAngle,
            indexAngle = indexAngle,
            middleAngle = middleAngle,
            ringAngle = ringAngle,
            littleAngle = littleAngle,
            modifier = Modifier.fillMaxSize()
          )

          // Overlay status badge
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .background(Color(0xFF334155), RoundedCornerShape(12.dp))
              .padding(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text(
              text = "ESP32 Servo Bus (5x)",
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFF38BDF8),
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Individual Finger Controls
      FingerControlRow(
        label = "Thumb",
        currentAngle = thumbAngle,
        onAngleSelected = { viewModel.setThumbAngle(it) }
      )

      FingerControlRow(
        label = "Index",
        currentAngle = indexAngle,
        onAngleSelected = { viewModel.setIndexAngle(it) }
      )

      FingerControlRow(
        label = "Middle",
        currentAngle = middleAngle,
        onAngleSelected = { viewModel.setMiddleAngle(it) }
      )

      FingerControlRow(
        label = "Ring",
        currentAngle = ringAngle,
        onAngleSelected = { viewModel.setRingAngle(it) }
      )

      FingerControlRow(
        label = "Little",
        currentAngle = littleAngle,
        onAngleSelected = { viewModel.setLittleAngle(it) }
      )

      Spacer(modifier = Modifier.height(20.dp))

      // Preset Action Buttons Grid
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        PresetButton(
          title = "OPEN HAND",
          icon = Icons.Outlined.PanTool,
          modifier = Modifier.weight(1f),
          onClick = { viewModel.setOpenHand() }
        )

        PresetButton(
          title = "CLOSE HAND",
          icon = Icons.Outlined.BackHand,
          modifier = Modifier.weight(1f),
          onClick = { viewModel.setCloseHand() }
        )

        PresetButton(
          title = "HOME POSITION",
          icon = Icons.Outlined.Home,
          modifier = Modifier.weight(1f),
          onClick = { viewModel.setHomePosition() }
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      // SEND COMMAND Button
      Button(
        onClick = {
          viewModel.sendHandControlCommand(context) { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
      ) {
        Icon(
          imageVector = Icons.Outlined.Send,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "SEND COMMAND",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          letterSpacing = 0.5.sp
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // RESET POSITION Button
      Button(
        onClick = { viewModel.setHomePosition() },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = lightBlueBtn)
      ) {
        Icon(
          imageVector = Icons.Outlined.RestartAlt,
          contentDescription = null,
          tint = primaryBlue,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "RESET POSITION",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = primaryBlue,
          letterSpacing = 0.5.sp
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // EMERGENCY STOP Button
      Button(
        onClick = {
          viewModel.emergencyStop(context) { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp)
          .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = redEmergency)
      ) {
        Icon(
          imageVector = Icons.Outlined.Warning,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "EMERGENCY STOP",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White,
          letterSpacing = 0.8.sp
        )
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
fun FingerControlRow(
  label: String,
  currentAngle: Int,
  onAngleSelected: (Int) -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 10.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1E293B),
        modifier = Modifier.width(70.dp)
      )

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        listOf(0, 90, 180).forEach { angle ->
          val isSelected = currentAngle == angle
          val activeBlue = Color(0xFF2F80ED)

          Box(
            modifier = Modifier
              .width(60.dp)
              .height(38.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(if (isSelected) activeBlue else Color.White)
              .border(
                width = 1.dp,
                color = if (isSelected) activeBlue else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(12.dp)
              )
              .clickable { onAngleSelected(angle) },
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "$angle°",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) Color.White else Color(0xFF64748B)
            )
          }
        }
      }
    }
  }
}

@Composable
fun PresetButton(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Card(
    modifier = modifier
      .height(96.dp)
      .clickable { onClick() },
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = Color(0xFF2F80ED),
        modifier = Modifier.size(28.dp)
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF2F80ED),
        fontSize = 10.sp,
        textAlign = TextAlign.Center
      )
    }
  }
}


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
        val cameraZ = 600f
        val scale = 2.8f // Increased size
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
            v = v.rotateX(java.lang.Math.toRadians(-currentLocalAngleX.toDouble()).toFloat())
            // Thumb sweeps across palm when closing
            v = v.rotateY(java.lang.Math.toRadians((currentLocalAngleX * 0.7f).toDouble()).toFloat())
        } else {
            v = v.rotateX(java.lang.Math.toRadians(-currentLocalAngleX.toDouble()).toFloat())
        }
        
        v = v.rotateZ(java.lang.Math.toRadians(splayZ.toDouble()).toFloat())
        
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
            // Draw segment body
            drawPath(segPath, color = fillColor)
            // Draw segment wireframe
            drawPath(segPath, color = wireColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            
            // Wireframe center axis
            drawLine(wireColor.copy(alpha = 0.5f), prevP, nextP, strokeWidth = 1f)
        }
        
        // Draw Joint (sphere projection)
        drawCircle(fillColor, r1, prevP)
        drawCircle(wireColor, r1, prevP, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        drawArc(wireColor.copy(alpha=0.6f), 0f, 180f, false, topLeft = Offset(prevP.x - r1, prevP.y - r1*0.5f), size = Size(r1*2f, r1), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
        
        prevP = nextP
        prevF = nextF
        currentRadius = nextRadius
        
        // Draw illuminated fingertip
        if (i == 2) {
            drawCircle(fillColor, r2, nextP)
            drawCircle(wireColor, r2, nextP, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            val tipColor = if (curlX > 45f) activeTipColor else wireColor
            drawCircle(tipColor, r2 * 0.7f, nextP) 
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

  // Metallic dark grey for the fill to look like 3D render
  val fillColor = Color(0xFF2C313C)
  // Neon cyan for the wireframe/mesh
  val wireColor = Color(0xFF00E5FF) 
  val activeTipColor = Color(0xFFFF0055) // Neon pink for active tips
  Canvas(modifier = modifier) {
      val cx = size.width / 2f
      val cy = size.height * 0.8f // Move base slightly down for larger hand

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
      drawPath(palmPath, color = wireColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
      
      // Wireframe grid lines for palm
      drawLine(wireColor.copy(alpha=0.6f), pTB, pKM, strokeWidth = 1.5f)
      drawLine(wireColor.copy(alpha=0.6f), pTB, pKR, strokeWidth = 1.5f)
      drawLine(wireColor.copy(alpha=0.6f), pWL, pKL, strokeWidth = 1.5f)
      drawLine(wireColor.copy(alpha=0.6f), pWL, pKM, strokeWidth = 1.5f)
      drawLine(wireColor.copy(alpha=0.6f), pWR, pKI, strokeWidth = 1.5f)
      
      // Horizontal cross-contours
      drawLine(wireColor.copy(alpha=0.4f), pKI, pKL, strokeWidth = 1f)

      // Draw all fingers in back-to-front projection sequence
      drawFinger(knuckleL, listOf(30f, 20f, 15f), 15f, animLittle, 9f, cx, cy, fillColor, wireColor, activeTipColor)
      drawFinger(knuckleR, listOf(40f, 25f, 20f), 5f, animRing, 10f, cx, cy, fillColor, wireColor, activeTipColor)
      drawFinger(knuckleM, listOf(45f, 30f, 22f), -2f, animMiddle, 11f, cx, cy, fillColor, wireColor, activeTipColor)
      drawFinger(knuckleI, listOf(40f, 25f, 20f), -10f, animIndex, 10f, cx, cy, fillColor, wireColor, activeTipColor)
      drawFinger(thumbBase, listOf(35f, 25f, 20f), -45f, animThumb, 12f, cx, cy, fillColor, wireColor, activeTipColor, isThumb = true)
  }
}
