package com.familyhub.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.familyhub.data.media.YandexDiskImageResolver
import com.familyhub.util.ExternalImageUrlUtils

@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bundledDrawable = remember(url) {
        FamilyAvatarCatalog.drawableFor(url) ?: BuiltInRecipeImageCatalog.drawableFor(url)
    }
    if (bundledDrawable != null) {
        Image(
            painter = painterResource(bundledDrawable),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
        return
    }

    val normalized = remember(url) { ExternalImageUrlUtils.normalizeInput(url) }
    var displayUrl by remember(normalized) { mutableStateOf<String?>(null) }

    LaunchedEffect(normalized) {
        displayUrl = when {
            normalized == null -> null
            ExternalImageUrlUtils.isLocalContentUri(normalized) -> normalized
            ExternalImageUrlUtils.isYandexDiskPublicUrl(normalized) ->
                YandexDiskImageResolver.resolveForDisplay(normalized)
            else -> normalized
        }
    }

    val model = displayUrl
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
