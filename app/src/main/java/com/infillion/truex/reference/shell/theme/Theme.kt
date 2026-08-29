package com.infillion.truex.reference.shell.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.infillion.truex.reference.R

private val BeVietnamPro = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
)

private val InfillionColors = darkColorScheme(
    primary = BloomPink,
    onPrimary = FogGray,
    secondary = BloomPurple,
    background = Charcoal,
    onBackground = FogGray,
    surface = CharcoalDeep,
    onSurface = FogGray,
)

@Composable
fun TrueXReferenceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InfillionColors,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Bold,
                fontSize = 46.sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
            ),
            titleMedium = TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            ),
            labelMedium = TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        ),
        content = content,
    )
}

