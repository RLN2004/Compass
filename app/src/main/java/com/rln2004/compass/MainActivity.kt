package com.rln2004.compass

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import kotlin.math.*

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private lateinit var compassView: CompassView
    private var heading = 0f
    private var targetHeading = 0f
    private var pitch = 0f
    private var roll = 0f
    private var sensorReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 15, 30)
        window.navigationBarColor = Color.rgb(7, 15, 30)
        window.decorView.systemUiVisibility = 0
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        compassView = CompassView(this)
        setContentView(compassView)
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        SensorManager.getOrientation(matrix, orientation)
        targetHeading = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f)
        pitch = orientation[1]
        roll = orientation[2]
        if (!sensorReady) {
            heading = targetHeading
            sensorReady = true
        }
        compassView.invalidate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private inner class CompassView(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private fun d(v: Float) = v * density
        private fun color(hex: Long) = (hex or 0xFF000000).toInt()
        private val bgTop = color(0xFF0B1730)
        private val bgBottom = color(0xFF101F3D)
        private val white = color(0xFFF4F7FF)
        private val muted = color(0xFF9AAFD2)
        private val cyan = color(0xFF65D8E8)
        private val red = color(0xFFFF657A)
        private val line = color(0xFF263B60)
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        private var lastFrame = 0L
        private var displayedHeading = 0f
        private var pulse = 0f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawColor(bgTop)
            p.shader = LinearGradient(0f, 0f, w, h, bgTop, bgBottom, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, p)
            p.shader = null

            val now = System.nanoTime()
            val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1e9f).coerceIn(0f, 0.05f)
            lastFrame = now
            var delta = ((targetHeading - displayedHeading + 540f) % 360f) - 180f
            displayedHeading = (displayedHeading + delta * min(1f, dt * 9f) + 360f) % 360f
            pulse = (pulse + dt * 0.65f) % (2f * Math.PI.toFloat())
            val tiltX = sin(roll.toDouble()).toFloat()
            val tiltY = sin(pitch.toDouble()).toFloat()

            // Subtle ambient glow gives the screen a layered, instrument-like depth.
            p.shader = RadialGradient(w * 0.5f, h * 0.48f, w * 0.72f,
                intArrayOf(color(0xFF1C3760), color(0x00101F3D)), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, p)
            p.shader = null

            val pad = d(25f)
            drawText(canvas, "FIELD INSTRUMENT  /  01", pad, d(42f), d(10f), muted, Paint.Align.LEFT, 0.16f)
            drawText(canvas, "COMPASS", pad, d(76f), d(25f), white, Paint.Align.LEFT)
            drawText(canvas, "L I T E", pad, d(98f), d(10f), cyan, Paint.Align.LEFT, 0.24f)
            // Small live indicator
            p.color = cyan
            p.setShadowLayer(d(7f), 0f, 0f, cyan)
            canvas.drawCircle(w - pad - d(5f), d(68f), d(3.5f), p)
            p.clearShadowLayer()
            drawText(canvas, "LIVE", w - pad - d(14f), d(72f), d(9f), muted, Paint.Align.RIGHT, 0.12f)

            // Heading readout panel
            val cardTop = d(125f)
            val cardBottom = d(226f)
            roundRect(canvas, pad, cardTop, w - pad, cardBottom, d(22f), color(0xFF172947), color(0xFF2A4166))
            drawText(canvas, "MAGNETIC HEADING", pad + d(18f), cardTop + d(25f), d(9f), muted, Paint.Align.LEFT, 0.13f)
            val deg = displayedHeading.roundToInt().mod(360)
            drawText(canvas, String.format("%03d", deg), pad + d(17f), cardTop + d(77f), d(47f), white, Paint.Align.LEFT)
            drawText(canvas, "°", pad + d(118f), cardTop + d(73f), d(27f), cyan, Paint.Align.LEFT)
            val direction = directionFor(deg)
            drawText(canvas, direction, w - pad - d(18f), cardTop + d(68f), d(17f), cyan, Paint.Align.RIGHT)
            drawText(canvas, "0° N", w - pad - d(18f), cardTop + d(88f), d(10f), muted, Paint.Align.RIGHT)

            // Main compass face: faux perspective, layered glow and glass rings.
            val availableTop = d(246f)
            val availableBottom = h - d(112f)
            val cx = w * 0.5f + tiltX * d(7f)
            val cy = (availableTop + availableBottom) * 0.5f + tiltY * d(7f)
            val radius = min(w * 0.415f, (availableBottom - availableTop) * 0.44f).coerceAtLeast(d(95f))
            p.color = color(0x4015D6E8)
            p.setShadowLayer(d(24f), 0f, d(5f), color(0x503BCBDF))
            canvas.drawCircle(cx, cy, radius + d(3f), p)
            p.clearShadowLayer()
            p.shader = RadialGradient(cx, cy, radius, intArrayOf(color(0xFF20385B), color(0xFF0B1730), color(0xFF0A1428)), floatArrayOf(0f, 0.78f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = d(2f)
            p.color = color(0xFF48668F)
            canvas.drawCircle(cx, cy, radius, p)
            p.strokeWidth = d(1f)
            p.color = color(0xFF223A5F)
            canvas.drawCircle(cx, cy, radius - d(9f), p)
            p.style = Paint.Style.FILL

            canvas.save()
            canvas.rotate(-displayedHeading, cx, cy)
            for (i in 0 until 72) {
                val a = Math.toRadians((i * 5 - 90).toDouble())
                val major = i % 6 == 0
                val medium = i % 3 == 0
                val outer = radius - d(13f)
                val inner = outer - d(if (major) 17f else if (medium) 11f else 5f)
                p.color = if (major) cyan else muted
                p.alpha = if (major) 230 else if (medium) 150 else 85
                p.strokeWidth = d(if (major) 2f else 1.2f)
                canvas.drawLine(cx + cos(a).toFloat() * inner, cy + sin(a).toFloat() * inner,
                    cx + cos(a).toFloat() * outer, cy + sin(a).toFloat() * outer, p)
            }
            p.alpha = 255
            drawCompassLabel(canvas, "N", cx, cy - radius + d(39f), red)
            drawCompassLabel(canvas, "E", cx + radius - d(39f), cy + d(6f), white)
            drawCompassLabel(canvas, "S", cx, cy + radius - d(25f), white)
            drawCompassLabel(canvas, "W", cx - radius + d(39f), cy + d(6f), white)
            // Needle has a soft shadow and two contrasting facets.
            val needleLength = radius * 0.62f
            val north = Path().apply {
                moveTo(cx, cy - needleLength)
                lineTo(cx - d(12f), cy + d(4f))
                lineTo(cx, cy - d(5f))
                lineTo(cx + d(12f), cy + d(4f))
                close()
            }
            p.setShadowLayer(d(8f), 0f, d(3f), color(0x90000000))
            p.color = red
            canvas.drawPath(north, p)
            p.clearShadowLayer()
            val south = Path().apply {
                moveTo(cx, cy + needleLength * 0.78f)
                lineTo(cx - d(12f), cy - d(4f))
                lineTo(cx, cy + d(5f))
                lineTo(cx + d(12f), cy - d(4f))
                close()
            }
            p.color = color(0xFFE5ECFA)
            canvas.drawPath(south, p)
            canvas.restore()

            // Glass-like center cap
            p.shader = RadialGradient(cx - d(2f), cy - d(3f), d(15f),
                intArrayOf(Color.WHITE, color(0xFF9FB7D9), color(0xFF324B70)), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, d(10f), p)
            p.shader = null
            p.color = color(0xFF101E37)
            canvas.drawCircle(cx, cy, d(4f), p)

            // Bottom info tiles
            val tileY = h - d(91f)
            val gap = d(10f)
            val tileW = (w - pad * 2 - gap) / 2f
            roundRect(canvas, pad, tileY, pad + tileW, tileY + d(55f), d(15f), color(0xFF172947), color(0xFF2A4166))
            roundRect(canvas, pad + tileW + gap, tileY, w - pad, tileY + d(55f), d(15f), color(0xFF172947), color(0xFF2A4166))
            drawText(canvas, "DIRECTION", pad + d(13f), tileY + d(19f), d(9f), muted, Paint.Align.LEFT, 0.12f)
            drawText(canvas, directionName(deg), pad + d(13f), tileY + d(41f), d(16f), white, Paint.Align.LEFT)
            drawText(canvas, "BEARING", pad + tileW + gap + d(13f), tileY + d(19f), d(9f), muted, Paint.Align.LEFT, 0.12f)
            drawText(canvas, "$deg°", pad + tileW + gap + d(13f), tileY + d(41f), d(16f), white, Paint.Align.LEFT)
            drawText(canvas, if (rotationSensor == null) "SENSOR UNAVAILABLE" else "TILT GENTLY TO CALIBRATE  •  KEEP AWAY FROM MAGNETS",
                w / 2f, h - d(17f), d(8f), muted, Paint.Align.CENTER, 0.04f)

            if (abs(delta) > 0.12f) postInvalidateOnAnimation()
        }

        private fun roundRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, fill: Int, stroke: Int) {
            p.shader = LinearGradient(l, t, r, b, fill, color(0xFF101F38), Shader.TileMode.CLAMP)
            p.style = Paint.Style.FILL
            p.color = fill
            c.drawRoundRect(l, t, r, b, radius, radius, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = d(1f)
            p.color = stroke
            c.drawRoundRect(l, t, r, b, radius, radius, p)
            p.style = Paint.Style.FILL
        }

        private fun drawText(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align, spacing: Float = 0f) {
            text.color = color
            text.textSize = size
            text.textAlign = align
            text.letterSpacing = spacing
            c.drawText(value, x, y, text)
        }

        private fun drawCompassLabel(c: Canvas, value: String, x: Float, y: Float, color: Int) {
            drawText(c, value, x, y, d(16f), color, Paint.Align.CENTER)
        }

        private fun directionFor(degrees: Int): String {
            val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            return names[((degrees + 22) % 360) / 45]
        }

        private fun directionName(degrees: Int): String {
            val names = arrayOf("North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West")
            return names[((degrees + 22) % 360) / 45]
        }
    }
}
