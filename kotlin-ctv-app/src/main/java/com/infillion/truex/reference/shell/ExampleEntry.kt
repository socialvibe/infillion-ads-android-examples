package com.infillion.truex.reference.shell

import android.app.Activity
import androidx.annotation.DrawableRes

data class ExampleEntry(
    val title: String,
    val description: String,
    val delivery: String,
    val insertion: String,
    @DrawableRes val artwork: Int,
    val destination: Class<out Activity>,
)

