package com.infillion.truex.reference.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infillion.truex.reference.R
import com.infillion.truex.reference.shell.theme.BloomOrange
import com.infillion.truex.reference.shell.theme.BloomPink
import com.infillion.truex.reference.shell.theme.BloomPurple
import com.infillion.truex.reference.shell.theme.Charcoal
import com.infillion.truex.reference.shell.theme.CharcoalDeep
import com.infillion.truex.reference.shell.theme.FogGray
import com.infillion.truex.reference.shell.theme.MutedFog

@Composable
fun ImmersiveListScreen(
    examples: List<ExampleEntry>,
    onOpenExample: (ExampleEntry) -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val selected = examples[selectedIndex]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Charcoal),
    ) {
        Image(
            painter = painterResource(selected.artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Charcoal,
                            0.48f to Charcoal.copy(alpha = 0.88f),
                            0.78f to Charcoal.copy(alpha = 0.22f),
                            1f to Color.Transparent,
                        ),
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Charcoal.copy(alpha = 0.18f),
                            0.52f to Color.Transparent,
                            1f to Charcoal,
                        ),
                    )
                },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 64.dp, vertical = 44.dp),
        ) {
            InfillionWordmark()
            Spacer(modifier = Modifier.weight(1f))

            Column(modifier = Modifier.width(760.dp)) {
                Text(
                    text = selected.title,
                    color = FogGray,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 51.sp,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = selected.description,
                    color = MutedFog,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 29.sp,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetadataChip(selected.delivery)
                    MetadataChip(selected.insertion)
                    MetadataChip("TrueX + IDVx")
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(examples) { index, example ->
                    ExampleCard(
                        example = example,
                        requestInitialFocus = index == 0,
                        onFocused = { selectedIndex = index },
                        onClick = { onOpenExample(example) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExampleCard(
    example: ExampleEntry,
    requestInitialFocus: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { androidx.compose.runtime.mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(20.dp)
    val borderWidth = if (focused) 4.dp else 1.dp
    val innerShape = RoundedCornerShape(20.dp - borderWidth)
    val borderBrush = Brush.linearGradient(listOf(BloomPurple, BloomPink, BloomOrange))

    Box(
        modifier = Modifier
            .size(width = 252.dp, height = 142.dp)
            .graphicsLayer {
                scaleX = if (focused) 1.08f else 1f
                scaleY = if (focused) 1.08f else 1f
            }
            .clip(shape)
            .background(
                if (focused) {
                    borderBrush
                } else {
                    Brush.linearGradient(
                        listOf(FogGray.copy(alpha = 0.2f), FogGray.copy(alpha = 0.2f)),
                    )
                },
            )
            .padding(borderWidth)
            .clip(innerShape)
            .background(CharcoalDeep)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent {
                if (
                    it.type == KeyEventType.KeyUp &&
                    (it.key == Key.Enter || it.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .semantics { contentDescription = "Open ${example.title}" }
            .focusable(),
    ) {
        Image(
            painter = painterResource(example.artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, CharcoalDeep.copy(alpha = 0.9f)),
                    ),
                ),
        )
        Text(
            text = example.title,
            color = FogGray,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
        )
    }

    if (requestInitialFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
private fun MetadataChip(label: String) {
    Text(
        text = label,
        color = FogGray,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, FogGray.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .background(Charcoal.copy(alpha = 0.52f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun InfillionWordmark() {
    Image(
        painter = painterResource(R.drawable.infillion_logo),
        contentDescription = "Infillion",
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(width = 192.dp, height = 76.dp),
    )
}
