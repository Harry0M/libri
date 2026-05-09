package com.theblankstate.libri.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.max

val LibriShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object LibriExpressivePolygons {
    val loading = listOf(
        MaterialShapes.Cookie6Sided,
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Puffy
    )
    val badge = MaterialShapes.Cookie6Sided
    val feature = MaterialShapes.SoftBoom
}

class RoundedPolygonShape(
    private val polygon: RoundedPolygon
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = polygon.toPath().asComposePath()
        val bounds = polygon.bounds()
        val maxDimension = max(bounds.width, bounds.height)
        val matrix = Matrix()

        matrix.scale(size.width / maxDimension, size.height / maxDimension)
        matrix.translate(-bounds.left, -bounds.top)
        path.transform(matrix)

        return Outline.Generic(path)
    }
}

private fun RoundedPolygon.bounds(): Rect {
    val bounds = calculateBounds()
    return Rect(
        left = bounds[0],
        top = bounds[1],
        right = bounds[2],
        bottom = bounds[3]
    )
}
