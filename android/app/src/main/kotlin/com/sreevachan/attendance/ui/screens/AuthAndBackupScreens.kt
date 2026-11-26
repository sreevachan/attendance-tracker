package com.sreevachan.attendance.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sreevachan.attendance.ui.EncModalMode
import com.sreevachan.attendance.ui.theme.LocalAttendanceColors
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val c = LocalAttendanceColors.current
    var locked by remember { mutableStateOf(false) }
    var showCopy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(250)
        locked = true
        delay(1000)
        showCopy = true
    }

    val shackleLift by animateFloatAsState(
        targetValue = if (locked) 0f else -14f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "shackleLift",
    )
    val shackleTilt by animateFloatAsState(
        targetValue = if (locked) 0f else 22f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "shackleTilt",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (showCopy) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "welcomeAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(modifier = Modifier.size(120.dp, 140.dp)) {
                val sx = size.width / 120f
                val sy = size.height / 140f
                val shacklePath = Path().apply {
                    moveTo(38f * sx, 62f * sy)
                    lineTo(38f * sx, 42f * sy)
                    cubicTo(38f * sx, 16f * sy, 82f * sx, 16f * sy, 82f * sx, 42f * sy)
                    lineTo(82f * sx, 62f * sy)
                }
                translate(top = shackleLift * sy) {
                    rotate(degrees = shackleTilt, pivot = Offset(82f * sx, 62f * sy)) {
                        drawPath(
                            path = shacklePath,
                            color = c.accent,
                            style = Stroke(width = 10f * sx, cap = StrokeCap.Round),
                        )
                    }
                }
                drawRoundRect(
                    color = c.accent,
                    topLeft = Offset(22f * sx, 62f * sy),
                    size = Size(76f * sx, 62f * sy),
                    cornerRadius = CornerRadius(14f * sx, 14f * sy),
                )
                drawCircle(color = Color.White, radius = 7f * sx, center = Offset(60f * sx, 88f * sy))
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(57f * sx, 92f * sy),
                    size = Size(6f * sx, 14f * sy),
                    cornerRadius = CornerRadius(3f * sx, 3f * sy),
                )
            }

            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.alpha(contentAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Your attendance, private.",
                    color = c.ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "You choose how your backup is protected. With end-to-end encryption, only you can read your attendance, not even the developer.",
                    color = c.mute,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                ) {
                    Text("Continue", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun AuthGateScreen(
    error: String,
    busy: Boolean,
    accountMismatch: Boolean,
    allowGuest: Boolean,
    onSignIn: () -> Unit,
    onContinueGuest: () -> Unit,
    onUseDifferentAccount: () -> Unit,
    onEraseDeviceData: () -> Unit,
) {
    val c = LocalAttendanceColors.current
    var guestArmed by remember { mutableStateOf(false) }
    var eraseArmed by remember { mutableStateOf(false) }

    LaunchedEffect(eraseArmed) {
        if (eraseArmed) {
            delay(4000)
            eraseArmed = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Attendance", color = c.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Sign in with Google to continue.", color = c.mute, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))

            Button(
                onClick = onSignIn,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
                }
            }

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = c.bad, fontSize = 14.sp, textAlign = TextAlign.Center)
            }

            if (accountMismatch) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onUseDifferentAccount,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, c.line),
                ) {
                    Text("Use a different account", color = c.ink, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (!eraseArmed) eraseArmed = true else onEraseDeviceData()
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, c.bad),
                ) {
                    Text(
                        text = if (eraseArmed) "Tap again to erase this device's data" else "Erase this device's data and continue",
                        color = c.bad,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else if (allowGuest) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (!guestArmed) guestArmed = true else onContinueGuest()
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, c.line),
                ) {
                    Text(
                        text = if (guestArmed) "I understand, continue without an account" else "Continue without an account",
                        color = c.ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (guestArmed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Without an account, your data stays only on this phone. If you delete the app or lose the phone, it can't be recovered. You can sign in later.",
                        color = c.mute,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun BackupChoiceOverlay(
    onChooseEncrypted: () -> Unit,
    onChooseStandard: () -> Unit,
    onChooseOff: () -> Unit,
) {
    val c = LocalAttendanceColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.widthIn(max = 380.dp)) {
            Text(
                text = "Back up your attendance?",
                color = c.ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Choose how it is protected. You can change this later in Settings.",
                color = c.mute,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))

            Button(
                onClick = onChooseEncrypted,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("End-to-end encrypted (recommended)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Only you can read it. If you lose both your passphrase and recovery code, nobody can recover it.",
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onChooseStandard,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, c.line),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = c.card, contentColor = c.ink),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("Standard backup", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Signing in with Google brings it back, with no passphrase. The developer could technically read it.",
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = c.mute,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onChooseOff,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, c.line),
            ) {
                Text("Not now", color = c.ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun EncryptionOverlay(
    mode: EncModalMode,
    recoveryCode: String,
    error: String,
    busy: Boolean,
    onSetupPassphrase: (String, String) -> Unit,
    onConfirmRecovery: (Boolean) -> Unit,
    onUnlock: (String) -> Unit,
    onToggleAlt: () -> Unit,
) {
    val c = LocalAttendanceColors.current
    val clipboard = LocalClipboardManager.current
    var pw1 by remember(mode) { mutableStateOf("") }
    var pw2 by remember(mode) { mutableStateOf("") }
    var savedChecked by remember(mode) { mutableStateOf(false) }
    var unlockInput by remember(mode) { mutableStateOf("") }
    var copied by remember(mode) { mutableStateOf(false) }

    val (title, subtitle, goText) = when (mode) {
        EncModalMode.SET_PASSPHRASE -> Triple(
            "Create your passphrase",
            "This passphrase encrypts your backup on this phone. Nobody can reset it, not even the developer. If you lose it, your recovery code is the only way back in.",
            "Continue",
        )
        EncModalMode.SHOW_RECOVERY -> Triple(
            "Save your recovery code",
            "Write it down or save it somewhere safe. It is the only other way to open your backup, and it can't be shown again.",
            "Finish",
        )
        EncModalMode.UNLOCK_PASSPHRASE -> Triple(
            "Enter your passphrase",
            "Your backup is encrypted. Enter your passphrase to unlock it on this device.",
            "Unlock",
        )
        EncModalMode.UNLOCK_RECOVERY -> Triple(
            "Use your recovery code",
            "Enter the recovery code you saved when you set up your backup.",
            "Unlock",
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = c.mute, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))

            when (mode) {
                EncModalMode.SET_PASSPHRASE -> {
                    OutlinedTextField(
                        value = pw1,
                        onValueChange = { pw1 = it },
                        label = { Text("Passphrase (8+ characters)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pw2,
                        onValueChange = { pw2 = it },
                        label = { Text("Repeat passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                EncModalMode.SHOW_RECOVERY -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.card, RoundedCornerShape(10.dp))
                            .border(1.dp, c.line, RoundedCornerShape(10.dp))
                            .clickable {
                                clipboard.setText(AnnotatedString(recoveryCode))
                                copied = true
                            }
                            .padding(14.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = recoveryCode,
                                color = c.ink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (copied) "Copied to clipboard" else "Tap to copy",
                                color = c.accent,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { savedChecked = !savedChecked },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = savedChecked, onCheckedChange = { savedChecked = it })
                        Text("I saved this code somewhere safe", color = c.ink, fontSize = 14.sp)
                    }
                }
                EncModalMode.UNLOCK_PASSPHRASE, EncModalMode.UNLOCK_RECOVERY -> {
                    val isRec = mode == EncModalMode.UNLOCK_RECOVERY
                    OutlinedTextField(
                        value = unlockInput,
                        onValueChange = { unlockInput = it },
                        label = { Text(if (isRec) "XXXX-XXXX-XXXX-XXXX-..." else "Passphrase") },
                        visualTransformation = if (isRec) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = if (isRec) {
                            KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                        } else {
                            KeyboardOptions(keyboardType = KeyboardType.Password)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    when (mode) {
                        EncModalMode.SET_PASSPHRASE -> onSetupPassphrase(pw1, pw2)
                        EncModalMode.SHOW_RECOVERY -> onConfirmRecovery(savedChecked)
                        EncModalMode.UNLOCK_PASSPHRASE, EncModalMode.UNLOCK_RECOVERY -> onUnlock(unlockInput)
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(goText, fontWeight = FontWeight.SemiBold)
                }
            }

            if (mode != EncModalMode.SHOW_RECOVERY) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onToggleAlt,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, c.line),
                ) {
                    val altText = when (mode) {
                        EncModalMode.SET_PASSPHRASE -> "Back"
                        EncModalMode.UNLOCK_PASSPHRASE -> "Use recovery code instead"
                        EncModalMode.UNLOCK_RECOVERY -> "Use passphrase instead"
                        EncModalMode.SHOW_RECOVERY -> ""
                    }
                    Text(altText, color = c.ink, fontWeight = FontWeight.SemiBold)
                }
            }

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = c.bad, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
