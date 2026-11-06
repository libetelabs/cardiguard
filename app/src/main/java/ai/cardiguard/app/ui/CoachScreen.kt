package ai.cardiguard.app.ui

import ai.cardiguard.app.BuildConfig
import ai.cardiguard.app.core.CoachUi
import ai.cardiguard.app.domain.BrakeQuestion
import ai.cardiguard.app.domain.Cause
import ai.cardiguard.app.domain.DriveSession
import ai.cardiguard.app.domain.IncidentRecord
import ai.cardiguard.app.domain.IncidentType
import ai.cardiguard.app.domain.Playbook
import ai.cardiguard.app.domain.Signal
import ai.cardiguard.app.domain.Trigger
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.LocalPolice
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VoiceOverOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * 주야간은 우리가 정하지 않는다. 차가 정한다.
 * Pleos 는 uiMode 로 주야간을 쥐고 있고(mNightModeLocked=true) 앱은 그 값을 따라갈 뿐이다.
 * 낮에 검은 화면을 띄우면 햇빛에 씻겨 안 보이고, 밤에 흰 화면을 띄우면 유리에 반사된다.
 * 역할 이름은 그대로 두고 값만 모드별로 갈라 쓰기 때문에 사용처는 손댈 필요가 없다.
 */
private val Ink: Color                   // 바탕
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF0F1012) else Color(0xFFEDEFF3)
private val Panel: Color                 // 카드
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF17181B) else Color(0xFFFFFFFF)
private val Line: Color                  // 경계
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF2A2C30) else Color(0xFFD3D7DD)
private val Muted: Color                 // 보조 글자
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF8E9196) else Color(0xFF61666C)
private val Paper: Color                 // 주요 전경. 글자와 채운 버튼의 바탕
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFECEDEF) else Color(0xFF14161A)
private val Red: Color                   // 포인트. 선과 글자에 쓴다
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFD94A3D) else Color(0xFFC12A1B)
private val RedDeep: Color               // 면으로 깔 때
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF7E241D) else Color(0xFFF2CFC9)
private val RedInk: Color                // 꽉 찬 빨강 위의 글자
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFE9E6) else Color(0xFFFFFFFF)
private val RedWashInk: Color            // 옅게 깐 빨강 위의 글자
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFE9E6) else Color(0xFF8A2316)
private val Asphalt: Color               // 도로 바닥
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF17181B) else Color(0xFFC2C8D1)
private val CarBody: Color               // 앞차 차체. 바닥과 반대로 간다
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFE9EEF2) else Color(0xFF2B2F35)
private val CarGlass: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6F8494) else Color(0xFF8FA4B4)

/** 색은 하나뿐이다. 평상시 전경색, 사건이면 빨강. */
@Composable
private fun tone(active: IncidentRecord?): Color = if (active == null) Paper else Red

/** 심각도는 색이 아니라 빨강을 선으로 쓰는지 면으로 쓰는지로 가른다. */
private fun filled(active: IncidentRecord?): Boolean =
    active?.type == IncidentType.THREAT || active?.type == IncidentType.COLLISION

@Composable
fun CoachScreen(
    ui: CoachUi,
    onListen: () -> Unit,
    onDial: () -> Unit,
    onDismiss: () -> Unit,
    onSignal: (Signal) -> Unit,
    onHeard: (String) -> Unit,
    onDemoDrive: () -> Unit,
    onDemoPark: () -> Unit,
    onDemoBrake: () -> Unit,
    onLabel: (Long, Cause) -> Unit,
) {
    val active = ui.engine.active
    val color = tone(active)
    val alert = active != null
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(
                    // 평상시엔 색을 쓰지 않는다. 사건이 있을 때만 화면 위쪽이 물든다.
                    // 빨강은 화면 맨 윗단에서만 번지고 본문에는 닿지 않는다. 글자 대비를 빼앗기지 않기 위해서다.
                    if (filled(active)) Brush.verticalGradient(
                        0f to RedDeep.copy(alpha = 0.30f), 0.16f to Ink, 1f to Ink,
                    ) else SolidColor(Ink),
                ),
        ) {
            // 창이 줄면 글자와 여백을 같이 줄인다. 버튼은 줄이지 않는다.
            val h = maxHeight
            val scale = when {
                h >= 820.dp -> 1.0f
                h >= 560.dp -> 0.80f
                else -> 0.62f
            }
            val pad = when {
                scale >= 1.0f -> 44.dp
                scale >= 0.8f -> 28.dp
                else -> 14.dp
            }
            val m = Metrics(scale, narrow = maxWidth < 1000.dp)
            // 시연 패널은 폭이 넉넉할 때만 옆자리를 차지한다. 좁으면 본문을 밀어내지 않도록 겹쳐 띄운다.
            val wide = maxWidth >= 1100.dp
            var demo by rememberSaveable { mutableStateOf(false) }
            Row(
                Modifier.fillMaxSize().padding(pad),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    StatusStrip(ui, color, m, if (demoAllowed() && !demo) ({ demo = true }) else null)
                    Spacer(Modifier.height(m.room))
                    Box(Modifier.weight(1f)) {
                        when {
                            !ui.engine.parked -> DrivingPanel(ui, color, m)
                            active != null -> ParkedCard(active, ui, color, onDial, onDismiss, onLabel, m)
                            else -> IdlePanel(ui, m)
                        }
                    }
                    Spacer(Modifier.height(m.room))
                    ActionBar(ui, color, m, onListen, onDial, onDismiss)
                }
                if (demoAllowed() && wide && demo) {
                    DemoPanel(ui, onSignal, onHeard, onDemoDrive, onDemoPark, onDemoBrake) { demo = false }
                }
            }
            // 좁은 창에서는 시연 패널이 옆자리를 못 얻으니 겹쳐 띄운다.
            if (demoAllowed() && demo && !wide) {
                Box(Modifier.align(Alignment.TopEnd).padding(pad)) {
                    DemoPanel(ui, onSignal, onHeard, onDemoDrive, onDemoPark, onDemoBrake) { demo = false }
                }
            }
        }
    }
}

/** 창 높이에 따라 글자와 여백을 함께 줄인다. 액션 바 높이는 줄이지 않는다. */
private class Metrics(val scale: Float, val narrow: Boolean = false) {
    val headline = (56 * scale).sp
    val lead = (64 * scale).sp
    val title = (40 * scale).sp
    val body = (34 * scale).sp
    val label = (22 * scale).sp
    val stat = (38 * scale).sp
    val tile = (72 * scale).sp      // 대기 화면 타일의 큰 숫자
    val heroUnit = (26 * scale).sp
    val avatar = (44 * scale).dp
    val gap = (14 * scale).dp
    val room = (34 * scale).dp        // 블록 사이 숨 쉬는 자리
    val measure = (1180 * scale).dp   // 한 줄이 이보다 길면 눈이 되돌아오지 못한다
    val glyph = (112 * scale).dp       // 단계 그림
    val guard = (300 * scale).dp       // 대기 화면 방패
    val compact = scale < 0.8f
    val buttonH = if (scale < 0.7f) 76 else 96
}

/** 말하는 주체를 화면에 세운다. 같은 인물이 상황에 따라 다른 역할을 한다는 걸 고리 색으로 알린다. */
@Composable
private fun Speaker(color: Color, m: Metrics) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(m.avatar)
                .background(Panel, RoundedCornerShape(50))
                .border(3.dp, color, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text("CG", color = color, fontSize = m.label * 0.8f, fontWeight = FontWeight.Black)
        }
        Text("카디가드", color = Muted, fontSize = m.label, fontWeight = FontWeight.Bold)
    }
}

/** 지금 차에서 무엇을 읽고 있는지 늘 보이게 둔다. 차량 앱이라는 증거가 글이 아니라 화면에 있어야 한다. */
@Composable
private fun StatusStrip(ui: CoachUi, color: Color, m: Metrics, onDemo: (() -> Unit)?) {
    val active = ui.engine.active
    val state = when {
        active != null -> Playbook.guide(active.type).title
        !ui.engine.parked -> "주행 중"
        else -> "대기"
    }
    val solid = filled(active)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (solid) RedDeep.copy(alpha = 0.35f) else Panel, RoundedCornerShape(14.dp))
            .border(if (active != null) 2.dp else 1.dp, if (active != null) Red else Line, RoundedCornerShape(14.dp))
            .padding(horizontal = (26 * m.scale).dp, vertical = if (m.compact) 10.dp else (18 * m.scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size((10 * m.scale).dp).background(if (solid) RedWashInk else color, RoundedCornerShape(50)))
        Text(state, color = if (solid) RedWashInk else color, fontSize = m.title, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Pill(if (ui.engine.parked) "P" else "D", if (solid) RedWashInk else color, m)
        Pill("${(ui.engine.speedKmh ?: 0f).toInt()} km/h", if (solid) RedWashInk else Paper, m)
        ui.issues.lastOrNull()?.let { Pill("신호 확인 필요", Muted, m) }
        // 떠 있던 버튼을 띠 안으로 들였다. 밖에 두면 띠의 오른쪽 끝을 가려 잘려 보였다.
        onDemo?.let {
            OutlinedButton(it, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Line)) {
                Text("시연 모드", color = Muted, fontSize = m.label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, m: Metrics) {
    Text(
        text,
        color = color,
        fontSize = m.label,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .background(Ink, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 상태와 무관하게 늘 같은 자리에 있는 큰 버튼. 창이 줄어도 여기는 잘리지 않는다. */
@Composable
private fun ActionBar(
    ui: CoachUi,
    color: Color,
    m: Metrics,
    onListen: () -> Unit,
    onDial: () -> Unit,
    onDismiss: () -> Unit,
) {
    val active = ui.engine.active
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        when {
            ui.engine.parked && active != null -> {
                BigButton("112 연결", Modifier.weight(1f), onDial, Red, m.buttonH)
                BigButton("안내 끝내기", Modifier.weight(1f), onDismiss, Paper, m.buttonH)
            }
            active?.type == IncidentType.THREAT -> {
                BigButton("말하기", Modifier.weight(1f), onListen, color, m.buttonH)
                BigButton("112", Modifier.weight(1f), onDial, Red, m.buttonH)
            }
            else -> BigButton("말하기", Modifier.weight(1f), onListen, color, m.buttonH)
        }
    }
}

/**
 * 대기 화면. 로고 대신 오늘의 경호 기록을 둔다. 1층이 보여야 매 주행 쓰는 앱으로 읽힌다.
 *
 * 차 화면은 가로로 넓다. 숫자를 왼쪽에 몰면 오른쪽 2/3 가 빈 채 남아 덜 만든 화면으로
 * 읽힌다. 숫자는 띠로 폭 전체에 펼치고, 그 아래를 「무엇을 보고 있는지」와
 * 「무엇을 봤는지」 두 칸으로 갈라 채운다. 기록이 한 줄뿐이어도 머리글과 테두리가
 * 있어 목록으로 읽힌다.
 */
@Composable
private fun IdlePanel(ui: CoachUi, m: Metrics) {
    val today = ui.lastSession
    val brakes = ui.history.filter { it.trigger == Trigger.HARD_BRAKE }
    // 전부 세면 시내 주행에서 열 몇 건이 빨갛게 쌓여 부채로 읽힌다. 물어볼 것만 센다.
    val todayBrakes = brakes.filter { it.sessionId == ui.lastSession?.id }
    val pendingIds = BrakeQuestion.pending(todayBrakes).map { it.id }.toSet()
    val unlabeled = pendingIds.size
    val km = today?.distanceKm ?: 0.0
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(m.gap)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            Tile("오늘 주행", "%.1f".format(km), "km", Paper, Modifier.weight(1f), m)
            Tile("주행 시간", today?.let { durationLabel(it) } ?: "0분", "", Paper, Modifier.weight(1f), m)
            Tile("급제동", "${brakes.size}", "회", if (brakes.isNotEmpty()) Red else Paper, Modifier.weight(1f), m)
            Tile("원인 미입력", "$unlabeled", "건", if (unlabeled > 0) Red else Muted, Modifier.weight(1f), m)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            WatchCard(Modifier.weight(1f).fillMaxHeight(), m)
            HistoryCard(ui, pendingIds, Modifier.weight(1.15f).fillMaxHeight(), m)
        }
    }
}

/** 숫자 타일. 큰 숫자를 먼저 읽히게 하고 이름과 단위는 작게 붙인다. */
@Composable
private fun Tile(label: String, value: String, unit: String, color: Color, modifier: Modifier, m: Metrics) {
    Column(
        modifier
            .background(Panel, RoundedCornerShape(18.dp))
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(horizontal = (24 * m.scale).dp, vertical = (16 * m.scale).dp),
    ) {
        Text(label, color = Muted, fontSize = m.label, maxLines = 1)
        Spacer(Modifier.height((6 * m.scale).dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, color = color, fontSize = m.tile, fontWeight = FontWeight.Black, maxLines = 1, lineHeight = m.tile)
            if (unit.isNotEmpty()) Text(unit, color = Muted, fontSize = m.heroUnit, maxLines = 1)
        }
    }
}

/** 지금 무엇을 보고 있는지. 대기 화면의 빈 가운데를 설명이 아니라 표식으로 채운다. */
@Composable
private fun WatchCard(modifier: Modifier, m: Metrics) {
    Column(
        modifier
            .background(Panel, RoundedCornerShape(20.dp))
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding((24 * m.scale).dp),
    ) {
        Speaker(Paper, m)
        // 표식과 글을 한 축에 세운다. 표식만 가운데 두고 글을 왼쪽에 두면 둘이 따로 논다.
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Guard(Paper, alert = false, Modifier.size(m.guard))
            Spacer(Modifier.height((20 * m.scale).dp))
            Text("경호 중", color = Paper, fontSize = m.title, fontWeight = FontWeight.Black)
            Spacer(Modifier.height((10 * m.scale).dp))
            // 적어 둔 것만 적는다. 이 셋이 GuardService 가 실제로 받는 신호다.
            Text("급제동 · 에어백 · 문 열림을 봅니다.", color = Muted, fontSize = m.body, textAlign = TextAlign.Center)
            Text("화면을 꺼도 계속 봅니다.", color = Muted, fontSize = m.body, textAlign = TextAlign.Center)
        }
    }
}

/** 무엇을 봤는지. 머리글과 구분선을 둬서 한 줄만 있어도 목록으로 읽히게 한다. */
@Composable
private fun HistoryCard(ui: CoachUi, pendingIds: Set<Long>, modifier: Modifier, m: Metrics) {
    Column(
        modifier
            .background(Panel, RoundedCornerShape(20.dp))
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding((24 * m.scale).dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("최근 기록", color = Paper, fontSize = m.title, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("이 차에만 저장됩니다", color = Muted, fontSize = m.label)
        }
        Spacer(Modifier.height((14 * m.scale).dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        if (ui.history.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("출발하면 기록을 시작합니다.", color = Muted, fontSize = m.body)
            }
        } else {
            val rows = ui.history.takeLast(6).reversed()
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                rows.forEachIndexed { i, r ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = (14 * m.scale).dp),
                        horizontalArrangement = Arrangement.spacedBy((18 * m.scale).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(clockLabel(r.atMs), color = Muted, fontSize = m.body, fontWeight = FontWeight.Black)
                        Text(Playbook.guide(r.type).title, color = Paper, fontSize = m.body, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        // 묻지 않기로 한 급제동까지 빨갛게 두면 상한을 둔 뜻이 없어진다.
                        val waiting = r.id in pendingIds
                        Text(
                            r.cause?.label() ?: if (waiting) "원인 미입력" else "기록됨",
                            color = if (waiting) Red else Muted,
                            fontSize = m.body,
                            maxLines = 1,
                        )
                    }
                    if (i < rows.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha = 0.6f)))
                    }
                }
            }
        }
    }
}

/** 방패 표식. 위기일 때만 고리가 퍼져 나간다. */
@Composable
private fun Guard(color: Color, alert: Boolean, modifier: Modifier = Modifier) {
    val paper = Paper
    val asphalt = Asphalt
    val carBody = CarBody
    val carGlass = CarGlass
    val line = Line
    val muted = Muted
    val ink = Ink
    val red = Red
    val ring by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "ring",
    )
    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2
        if (alert) {
            drawCircle(color.copy(alpha = 0.5f * (1 - ring)), radius * (0.72f + 0.28f * ring), center, style = Stroke(10f))
        }
        val w = radius * 1.1f
        val h = radius * 1.3f
        val left = center.x - w / 2
        val top = center.y - h / 2
        val shield = Path().apply {
            moveTo(center.x, top)
            lineTo(left + w, top + h * 0.14f)
            lineTo(left + w, top + h * 0.5f)
            quadraticBezierTo(left + w, top + h * 0.82f, center.x, top + h)
            quadraticBezierTo(left, top + h * 0.82f, left, top + h * 0.5f)
            lineTo(left, top + h * 0.14f)
            close()
        }
        drawPath(shield, Brush.verticalGradient(listOf(color.copy(alpha = 0.5f), color.copy(alpha = 0.1f)), top, top + h))
        drawPath(shield, color, style = Stroke(9f))
        val mark = Stroke(16f)
        if (alert) {
            drawLine(paper, Offset(center.x, top + h * 0.26f), Offset(center.x, top + h * 0.58f), mark.width)
            drawCircle(paper, mark.width * 0.7f, Offset(center.x, top + h * 0.74f))
        } else {
            val check = Path().apply {
                moveTo(center.x - w * 0.24f, top + h * 0.5f)
                lineTo(center.x - w * 0.05f, top + h * 0.66f)
                lineTo(center.x + w * 0.27f, top + h * 0.33f)
            }
            drawPath(check, paper, style = mark)
        }
    }
}

/**
 * 뒤에서 따라가며 본 내 차와 보호막. 경호 범위를 나타내는 그림일 뿐 주변 차량을 감지한 표시가 아니다.
 * 앱에는 전방 카메라 입력이 없으므로 주변 차량이나 위험 대상을 그리지 않는다.
 */
@Composable
private fun Escort(color: Color, type: IncidentType?, speedKmh: Float, speaking: Boolean, modifier: Modifier) {
    val asphalt = Asphalt
    val carBody = CarBody
    val carGlass = CarGlass
    val line = Line
    val muted = Muted
    val ink = Ink
    val red = Red
    val beat by rememberInfiniteTransition(label = "escort").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "escort",
    )
    val alert = type != null
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val vanish = Offset(w / 2, h * 0.08f)
        // 깊이 t(0=지평선, 1=화면 아래)를 화면 좌표로 바꾼다. 멀수록 촘촘해 보이게 제곱한다.
        fun y(t: Float) = vanish.y + (h - vanish.y) * t * t
        fun x(t: Float, lane: Float) = w / 2 + lane * w * 0.62f * t

        // 도로 바닥과 가장자리
        val road = Path().apply {
            moveTo(x(0f, -1f), y(0f)); lineTo(x(1f, -1f), y(1f)); lineTo(x(1f, 1f), y(1f)); lineTo(x(0f, 1f), y(0f)); close()
        }
        drawPath(road, Brush.verticalGradient(listOf(Color.Transparent, asphalt.copy(alpha = 0.95f)), vanish.y, h))
        listOf(-1f, 1f).forEach { side -> drawLine(line, Offset(x(0f, side), y(0f)), Offset(x(1f, side), y(1f)), 6f) }

        // 차로 점선. 속도가 빠를수록 빨리 흐른다. 한 주기에 정수 칸만 움직여 끊김 없이 이어진다.
        val period = 0.16f
        val steps = when {
            speedKmh < 1f -> 0
            speedKmh < 40f -> 1
            speedKmh < 90f -> 2
            else -> 3
        }
        val shift = beat * period * steps
        listOf(-1f / 3, 1f / 3).forEach { lane ->
            var t = -period + shift % period
            while (t < 1f) {
                val a = t.coerceIn(0f, 1f)
                val b = (t + period * 0.5f).coerceIn(0f, 1f)
                if (b > a) drawLine(muted.copy(alpha = 0.25f + 0.5f * b), Offset(x(a, lane), y(a)), Offset(x(b, lane), y(b)), 3f + 9f * b)
                t += period
            }
        }

        // 내 차로 위로 깔리는 경호 범위. 멀어질수록 옅어진다.
        val carT = 0.86f
        val guardLane = Path().apply {
            moveTo(x(0.25f, -1f / 3), y(0.25f)); lineTo(x(carT, -1f / 3), y(carT)); lineTo(x(carT, 1f / 3), y(carT)); lineTo(x(0.25f, 1f / 3), y(0.25f)); close()
        }
        drawPath(guardLane, Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = if (alert) 0.55f else 0.32f)), y(0.25f), y(carT)))

        // 앞쪽 방향 표시. 경호원이 길을 여는 느낌으로 내 차로 위를 차례로 지나간다.
        if (steps > 0 || alert) {
            repeat(3) { i ->
                val t = 0.42f + 0.14f * i
                val glow = ((beat * 3 - i + 3) % 3).let { 1f - it / 3f }
                val half = w * 0.62f * t / 3 * 0.55f
                val chevron = Path().apply {
                    moveTo(w / 2 - half, y(t) + half * 0.35f); lineTo(w / 2, y(t)); lineTo(w / 2 + half, y(t) + half * 0.35f)
                }
                drawPath(chevron, color.copy(alpha = 0.25f + 0.65f * glow), style = Stroke(6f + 6f * t))
            }
        }

        // 내 차(뒷모습)
        val carW = w * 0.3f
        val carH = carW * 0.62f
        val carCenter = Offset(w / 2, y(carT))
        val carTop = carCenter.y - carH / 2
        val carLeft = carCenter.x - carW / 2

        // 보호막. 차를 감싸는 반구, 말할 때 숨 쉬듯 커진다.
        val breath = if (speaking) 0.06f * kotlin.math.sin(beat * 6.283f * 2) else 0f
        val domeW = carW * (1.9f + breath)
        val domeH = carH * (2.5f + breath)
        val domeTopLeft = Offset(carCenter.x - domeW / 2, carCenter.y + carH * 0.45f - domeH)
        drawArc(
            Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.04f)), domeTopLeft.y, domeTopLeft.y + domeH),
            180f, 180f, true, domeTopLeft, Size(domeW, domeH * 2),
        )
        drawArc(color, 180f, 180f, false, domeTopLeft, Size(domeW, domeH * 2), style = Stroke(if (speaking) 12f else 7f))

        // 위기일 때 보호막에서 퍼지는 파동
        if (alert) {
            val grow = 1f + 0.45f * beat
            drawArc(
                color.copy(alpha = 0.6f * (1 - beat)), 180f, 180f, false,
                Offset(carCenter.x - domeW * grow / 2, carCenter.y + carH * 0.45f - domeH * grow), Size(domeW * grow, domeH * grow * 2),
                style = Stroke(8f),
            )
        }

        drawRoundRect(Color(0xFF1C252E), Offset(carLeft + carW * 0.16f, carTop - carH * 0.42f), Size(carW * 0.68f, carH * 0.6f), CornerRadius(carW * 0.12f))
        drawRoundRect(carGlass, Offset(carLeft + carW * 0.22f, carTop - carH * 0.34f), Size(carW * 0.56f, carH * 0.38f), CornerRadius(carW * 0.08f))
        drawRoundRect(carBody, Offset(carLeft, carTop), Size(carW, carH), CornerRadius(carW * 0.14f))
        val lamp = if (alert) color else Color(0xFFE0393E)
        listOf(carLeft + carW * 0.06f, carLeft + carW * 0.72f).forEach { lx ->
            drawRoundRect(lamp, Offset(lx, carTop + carH * 0.2f), Size(carW * 0.22f, carH * 0.16f), CornerRadius(8f))
        }
        drawRoundRect(ink, Offset(carLeft + carW * 0.34f, carTop + carH * 0.52f), Size(carW * 0.32f, carH * 0.18f), CornerRadius(6f))
        listOf(carLeft + carW * 0.08f, carLeft + carW * 0.74f).forEach { tx ->
            drawRoundRect(ink, Offset(tx, carTop + carH * 0.92f), Size(carW * 0.18f, carH * 0.2f), CornerRadius(6f))
        }

        // 문 잠금 요청 표시(자물쇠). 사고 때는 잠그지 않으므로 켜지 않는다.
        if (type == IncidentType.THREAT) {
            listOf(-1f, 1f).forEach { side ->
                val c = Offset(carCenter.x + side * (carW / 2 + carW * 0.2f), carCenter.y)
                val s = carW * 0.09f
                drawArc(red, 180f, 180f, false, Offset(c.x - s * 0.7f, c.y - s * 1.5f), Size(s * 1.4f, s * 1.6f), style = Stroke(s * 0.35f))
                drawRoundRect(red, Offset(c.x - s, c.y - s * 0.7f), Size(s * 2, s * 1.6f), CornerRadius(s * 0.3f))
            }
        }
    }
}

/** 주행 중 화면. 한 문장과 큰 버튼만 둔다. */
@Composable
private fun DrivingPanel(ui: CoachUi, color: Color, m: Metrics) {
    val active = ui.engine.active
    Row(
        Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 좁은 창에서는 표식을 접고 문장만 남긴다. 주행 중엔 글자가 먼저다.
        if (!m.compact) Escort(color, active?.type, ui.engine.speedKmh ?: 0f, ui.speaking, Modifier.weight(1f).fillMaxHeight())
        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(m.gap)) {
            Speaker(color, m)
            Text(
                ui.lastLine?.takeIf { active != null } ?: "기록 중",
                color = Paper,
                fontSize = m.headline,
                lineHeight = m.headline * 1.3f,
                fontWeight = FontWeight.Bold,
            )
            // 말만 하지 않고 차가 실제로 한 일을 한 줄 남긴다.
            if (active?.type == IncidentType.THREAT) {
                Text("문 잠금 요청 완료", color = color, fontSize = m.body, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** P 전환 후 화면. 주행 중에 숨겼던 절차와 이번 주행 요약을 함께 보여준다. 사고 때 절차가 밀리지 않게 절차를 먼저 둔다. */
@Composable
private fun ParkedCard(
    record: IncidentRecord,
    ui: CoachUi,
    color: Color,
    onDial: () -> Unit,
    onDismiss: () -> Unit,
    onLabel: (Long, Cause) -> Unit,
    m: Metrics,
) {
    val guide = Playbook.guide(record.type)
    val saved = ui.history.lastOrNull { it.id == record.id } ?: record
    val steps = guide.parkedSteps
    // 한 화면에 한 단계만 둔다. 목록을 펼쳐 두면 읽을 것이 일곱 개로 보이고,
    // 무엇을 했는지도 남지 않는다. 눌러서 처리한 것만 기록한다.
    var step by remember(record.id) { mutableIntStateOf(0) }
    val handled = remember(record.id) { mutableStateMapOf<Int, Boolean>() }
    val finished = step >= steps.size
    val session = ui.lastSession

    // 절차와 이번 주행 요약을 나란히 둔다. 절차가 먼저라 왼쪽을 넓게 준다.
    // 좁은 창에서는 요약을 접는다 — 사고 때 절차가 밀리는 쪽이 더 비싸다.
    //
    // 요약을 붙이지 않으면 급제동 원인을 고르는 버튼(상대 때문·내 판단·그냥 감속)에
    // 닿을 길이 없어진다. 그 버튼은 DriveSummary 안에만 있고, 대기 화면의
    // 「원인 미입력」 숫자는 그 버튼을 눌러야 줄어든다.
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(m.room * 0.6f)) {
        Column(Modifier.weight(1.45f).fillMaxHeight()) {
            StepDots(guide.stepIcons, step, steps.size, handled, color, m)
            Spacer(Modifier.height(m.room))
            if (finished) {
                StepDone(guide.stepIcons, steps, handled, saved, color, m, onDismiss)
                return@Column
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Row(
                    Modifier.widthIn(max = m.measure),
                    horizontalArrangement = Arrangement.spacedBy(m.room),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    guide.stepIcons.getOrNull(step)?.let {
                        Icon(it.vector(), contentDescription = null, tint = color, modifier = Modifier.size(m.glyph))
                    }
                    Column {
                        Text(
                            "STEP %02d / %02d".format(step + 1, steps.size),
                            color = color,
                            fontSize = m.label,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                        )
                        Spacer(Modifier.height(m.room * 0.45f))
                        Text(
                            steps[step],
                            color = Paper,
                            fontSize = m.lead,
                            lineHeight = m.lead * 1.32f,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                "${Playbook.reportText(saved, timeLabel(saved.atMs), saved.placeLabel())}   ${Playbook.DISCLAIMER}",
                color = Muted,
                fontSize = m.label,
            )
            Spacer(Modifier.height(m.room * 0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(m.room * 0.6f)) {
                StepChoice("했어요", color, m, filled = true) {
                    handled[step] = true; step++
                }
                StepChoice("해당 없음", Muted, m, filled = false) {
                    handled[step] = false; step++
                }
                if (step > 0) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "이전",
                        color = Muted,
                        fontSize = m.body,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterVertically).clickable { step-- },
                    )
                }
            }
        }
        if (session != null && !m.narrow) {
            DriveSummary(session, ui.history, onLabel, Modifier.weight(1f).fillMaxHeight(), m)
        }
    }
}

/** 도메인이 정한 뜻을 그림으로 옮긴다. 뜻과 그림을 잇는 자리는 여기 하나뿐이다. */
private fun Playbook.StepIcon.vector(): ImageVector = when (this) {
    Playbook.StepIcon.MEDICAL -> Icons.Outlined.MedicalServices
    Playbook.StepIcon.HAZARD -> Icons.Outlined.WarningAmber
    Playbook.StepIcon.ESCAPE -> Icons.Outlined.DirectionsRun
    Playbook.StepIcon.PHOTO -> Icons.Outlined.PhotoCamera
    Playbook.StepIcon.INSURANCE -> Icons.Outlined.Description
    Playbook.StepIcon.POLICE -> Icons.Outlined.LocalPolice
    Playbook.StepIcon.FAULT -> Icons.Outlined.Balance
    Playbook.StepIcon.VIDEO -> Icons.Outlined.Videocam
    Playbook.StepIcon.ARCHIVE -> Icons.Outlined.Save
    Playbook.StepIcon.REPORT -> Icons.Outlined.Campaign
    Playbook.StepIcon.LOCK -> Icons.Outlined.Lock
    Playbook.StepIcon.NO_ARGUE -> Icons.Outlined.VoiceOverOff
    Playbook.StepIcon.TOW -> Icons.Outlined.CarRepair
}

/** 어디까지 왔고 무엇을 처리했는지. 채운 그림이 "했어요"다. */
@Composable
private fun StepDots(
    icons: List<Playbook.StepIcon>,
    current: Int,
    total: Int,
    handled: Map<Int, Boolean>,
    color: Color,
    m: Metrics,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.room * 0.4f)) {
        repeat(total) { i ->
            val done = handled[i]
            val tint = when {
                done == true -> color          // 했어요
                done == false -> Muted         // 해당 없음
                i == current -> color          // 지금
                else -> Line                   // 아직
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((6 * m.scale).dp),
            ) {
                icons.getOrNull(i)?.let {
                    Icon(
                        it.vector(),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(m.glyph * (if (i == current) 0.52f else 0.40f)),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((if (i == current) 6 else 3).dp * m.scale)
                        .background(tint, RoundedCornerShape(50)),
                )
            }
        }
    }
}

/** 전부 지나온 뒤. 무엇을 처리했는지 남겨야 안내가 끝났다고 할 수 있다. */
@Composable
private fun ColumnScope.StepDone(
    icons: List<Playbook.StepIcon>,
    steps: List<String>,
    handled: Map<Int, Boolean>,
    saved: IncidentRecord,
    color: Color,
    m: Metrics,
    onDismiss: () -> Unit,
) {
    val doneCount = handled.count { it.value }
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Column(Modifier.widthIn(max = m.measure), verticalArrangement = Arrangement.spacedBy(m.room * 0.5f)) {
            Text("안내 완료", color = color, fontSize = m.headline, fontWeight = FontWeight.Black)
            Text(
                "${steps.size}단계 중 ${doneCount}단계를 처리했습니다.",
                color = Paper,
                fontSize = m.body,
                fontWeight = FontWeight.Bold,
            )
            Text(
                Playbook.reportText(saved, timeLabel(saved.atMs), saved.placeLabel()),
                color = Muted,
                fontSize = m.label,
            )
        }
    }
    Spacer(Modifier.height(m.room))
    StepChoice("닫기", color, m, filled = true, onClick = onDismiss)
}

@Composable
private fun StepChoice(label: String, color: Color, m: Metrics, filled: Boolean, onClick: () -> Unit) {
    val h = (m.buttonH * 0.92f).dp
    if (filled) {
        Button(
            onClick,
            Modifier.widthIn(min = (260 * m.scale).dp).height(h),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = if (color == Red) RedInk else Ink),
        ) { Text(label, fontSize = m.title, fontWeight = FontWeight.Black) }
    } else {
        OutlinedButton(
            onClick,
            Modifier.widthIn(min = (220 * m.scale).dp).height(h),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(2.dp, Line),
        ) { Text(label, color = color, fontSize = m.title, fontWeight = FontWeight.Bold) }
    }
}


@Composable
private fun StepHeader(title: String, current: Int, total: Int, color: Color, m: Metrics) {
    Column(verticalArrangement = Arrangement.spacedBy((10 * m.scale).dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, color = color, fontSize = m.headline, fontWeight = FontWeight.Black, maxLines = 1)
            Text(
                "%02d / %02d".format(current, total),
                color = Muted,
                fontSize = m.label,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = (8 * m.scale).dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(total) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(if (i < current) color else Line),
                )
            }
        }
    }
}


/** 정차 후 주행 요약과 급제동 원인 표시. 주행 중에는 절대 묻지 않는다. */
@Composable
private fun DriveSummary(session: DriveSession, history: List<IncidentRecord>, onLabel: (Long, Cause) -> Unit, modifier: Modifier, m: Metrics) {
    val brakes = history.filter { it.sessionId == session.id && it.trigger == Trigger.HARD_BRAKE }
    Column(
        modifier
            .background(Panel.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("이번 주행", color = Paper, fontSize = m.title, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("거리", "%.1f km".format(session.distanceKm), Paper, Modifier.weight(1f), m)
            Stat("시간", durationLabel(session), Paper, Modifier.weight(1f), m)
            Stat("급제동", "${session.hardBrakes}회", if (session.hardBrakes > 0) Red else Paper, Modifier.weight(1f), m)
        }
        if (brakes.isEmpty()) {
            Text("급제동 없음", color = Muted, fontSize = m.body)
        } else {
            Text("급제동 원인", color = Paper, fontSize = m.body, fontWeight = FontWeight.Bold)
            if (m.narrow) {
                val ask = BrakeQuestion.asked(brakes).map { it.id }.toSet()
                brakes.forEach { BrakeRow(it, onLabel, asked = it.id in ask) }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val ask = BrakeQuestion.asked(brakes).map { it.id }.toSet()
                    items(brakes, key = { it.id }) { BrakeRow(it, onLabel, asked = it.id in ask) }
                }
            }
            Text("이 차에만 저장됩니다.", color = Muted, fontSize = m.label)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier, m: Metrics) {
    Column(modifier.background(Ink, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, color = Muted, fontSize = m.label, maxLines = 1)
        Text(value, color = color, fontSize = m.stat, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun BrakeRow(record: IncidentRecord, onLabel: (Long, Cause) -> Unit, asked: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val speed = record.vehicle.run { "${speedBefore?.toInt() ?: "-"} → ${speedAfter?.toInt() ?: "-"} km/h" }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(clockLabel(record.atMs), color = Muted, fontSize = 24.sp)
            Text(speed, color = Paper, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            record.cause?.let { Text(it.label(), color = Paper, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        }
        if (record.cause == null && asked) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Cause.OTHER, Cause.MINE, Cause.SLOWDOWN).forEach { cause ->
                    Button(
                        { onLabel(record.id, cause) },
                        Modifier.weight(1f).height(76.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Line, contentColor = Paper),
                    ) {
                        Text(cause.label(), fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun Cause.label() = when (this) {
    Cause.OTHER -> "상대 때문"
    Cause.MINE -> "내 판단"
    Cause.SLOWDOWN -> "그냥 감속"
    Cause.THREAT -> "위협"
}

/** 첫 실행 화면. */
/** 가짜 신호가 실제 차량에 들어가지 않도록 시연 입력은 에뮬레이터에서만 연다. */
fun demoAllowed(): Boolean = BuildConfig.DEBUG || Build.HARDWARE == "ranchu"

/** Virtual Console에 속도 카드가 없어 시연 신호를 여기서 넣는다. */
@Composable
private fun DemoPanel(
    ui: CoachUi,
    onSignal: (Signal) -> Unit,
    onHeard: (String) -> Unit,
    onDrive: () -> Unit,
    onPark: () -> Unit,
    onBrake: () -> Unit,
    onClose: () -> Unit,
) {
    fun now() = System.currentTimeMillis()
    Column(
        Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(Ink.copy(alpha = 0.96f), RoundedCornerShape(14.dp))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("시연 신호", color = Muted, fontSize = 18.sp)
        DemoButton("시연 모드 닫기", onClose)
        DemoButton("D 주행") {
            onSignal(Signal.Gear(false, now()))
            onDrive()
        }
        DemoButton("P 주차") {
            onPark()
            onSignal(Signal.Gear(true, now()))
        }
        DemoButton("급제동 80→40", onBrake)
        DemoButton("에어백 전개") { onSignal(Signal.Airbag(true, now())) }
        DemoButton("문 열림") { onSignal(Signal.DoorOpened(now())) }
        listOf("도와줘", "방금 박았어", "저장해", "네", "괜찮아").forEach { text ->
            DemoButton("\"$text\"") { onHeard(text) }
        }
    }
}

@Composable
private fun DemoButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.fillMaxWidth()) { Text(label, fontSize = 16.sp) }
}

@Composable
private fun BigButton(label: String, modifier: Modifier, onClick: () -> Unit, color: Color = Paper, heightDp: Int = 112) {
    Button(
        onClick,
        modifier.height(heightDp.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = if (color == Red) RedInk else Ink,
        ),
    ) {
        Text(label, fontSize = 42.sp, fontWeight = FontWeight.Black)
    }
}

/* 좌표는 신고와 보험 제출에 쓰는 값이지 화면에서 읽을 값이 아니다. 기록 여부만 알린다. */
private fun IncidentRecord.placeLabel(): String? =
    if (latitude != null && longitude != null) "위치 기록됨" else null

private fun timeLabel(atMs: Long): String =
    SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(atMs))

private fun clockLabel(atMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(atMs))

private fun durationLabel(session: DriveSession): String {
    val seconds = ((session.endedAtMs ?: session.startedAtMs) - session.startedAtMs) / 1_000
    return if (seconds < 60) "${seconds}초" else "${seconds / 60}분 ${seconds % 60}초"
}
