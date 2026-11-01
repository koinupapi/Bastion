package com.koinupapi.bastion

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel

/** Keeps loaded details across Activity recreation caused by rotation. */
internal class SeerrMediaCacheViewModel : ViewModel() {
    val media = mutableStateMapOf<String, SeerrMediaDetails>()
}
