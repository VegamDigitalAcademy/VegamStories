package com.vegamstories.app.presentation.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.vegamstories.app.core.logging.AppLogger

/**
 * A page pulled up into a fist like cloth (Android 13+, as a shader over the page): around the
 * fist the page is lifted towards it and gathered in from the sides, with soft creases fanning
 * out from it.
 */
internal class PageWrap @RequiresApi(Build.VERSION_CODES.TIRAMISU) constructor() {
    private val shader = RuntimeShader(SOURCE)

    /**
     * The page held at [pinch] (on the page, in pixels) and pulled up [pull] pixels there, the pull
     * reaching about [reach] pixels around; [creases] how creased (0 … 1); [gather] how much it's
     * drawn in from the sides (0 … 1).
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun effect(pinch: Offset, pull: Float, reach: Float, creases: Float, gather: Float): RenderEffect {
        shader.setFloatUniform("pinch", pinch.x, pinch.y)
        shader.setFloatUniform("pull", pull)
        shader.setFloatUniform("reach", reach)
        shader.setFloatUniform("creases", creases)
        shader.setFloatUniform("gather", gather)
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }

    companion object {
        /** A page wrap, if this phone can draw one (Android 13+, and the shader builds); else null. */
        fun orNull(): PageWrap? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            return try {
                PageWrap()
            } catch (e: RuntimeException) {
                log.w("PageWrap: shader unavailable", e)
                null
            }
        }

        private val log = AppLogger("PageWrap")

        private const val SOURCE = """
            uniform shader content;
            uniform float2 pinch;
            uniform float pull;
            uniform float reach;
            uniform float creases;
            uniform float gather;

            half4 main(float2 xy) {
                float2 d = xy - pinch;
                // How firmly the fist holds this bit of the page: fully at the fist, less further off
                // (reaching further sideways than down, as cloth pulled up from one point).
                float2 e = d * float2(0.75, 1.0);
                float hold = exp(-dot(e, e) / (reach * reach));
                // Lifted towards the fist, and drawn in from the sides.
                float2 from = xy + float2(-d.x * gather * hold, pull * hold);
                float4 c = float4(content.eval(from));
                // Creases fanning out from the fist: troughs a little darker, ridges a little lighter.
                float around = atan(d.y, d.x);
                float ridge = sin(around * 12.0 + length(d) * 0.015);
                float k = creases * exp(-length(e) / (reach * 0.9));
                c.rgb = c.rgb * (1.0 - 0.34 * k * (0.5 - 0.5 * ridge)) + 0.16 * k * max(ridge, 0.0) * c.a;
                c.rgb = min(c.rgb, float3(c.a));
                return half4(c);
            }
        """
    }
}
