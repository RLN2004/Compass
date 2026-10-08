package in.swalearn.compasslite

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private lateinit var dial: CompassDial
    private lateinit var headingText: TextView
    private var heading = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 22, 42)
        window.navigationBarColor = Color.rgb(12, 22, 42)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(Color.rgb(12, 22, 42))
        }
        root.addView(TextView(this).apply {
            text = "COMPASS LITE"
            textSize = 14f
            letterSpacing = 0.18f
            setTextColor(Color.rgb(164, 190, 230))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(36)))

        headingText = TextView(this).apply {
            text = "—°"
            textSize = 54f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(headingText, LinearLayout.LayoutParams(-1, dp(82)))

        root.addView(TextView(this).apply {
            text = "N     NE     E     SE     S     SW     W     NW"
            textSize = 11f
            setTextColor(Color.rgb(164, 190, 230))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(30)))

        dial = CompassDial(this)
        root.addView(dial, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(TextView(this).apply {
            text = if (rotationSensor == null) "Compass sensor unavailable" else "Hold phone flat and away from magnets"
            textSize = 13f
            setTextColor(Color.rgb(164, 190, 230))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(48)))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
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
        heading = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
        headingText.text = "${heading.toInt()}°"
        dial.invalidate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class CompassDial(context: Context) : View(context) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(46, 67, 101)
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
        }
        private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(164, 190, 230)
            strokeWidth = dp(2).toFloat()
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(18).toFloat()
            textAlign = Paint.Align.CENTER
        }
        private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 91, 91)
            style = Paint.Style.FILL
        }
        private val southPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(221, 231, 246)
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) * 0.39f
            canvas.drawCircle(cx, cy, radius, ring)

            for (i in 0 until 36) {
                val angle = Math.toRadians((i * 10 - 90).toDouble())
                val outer = radius - dp(5)
                val inner = outer - if (i % 3 == 0) dp(17) else dp(8)
                canvas.drawLine(
                    cx + cos(angle).toFloat() * inner,
                    cy + sin(angle).toFloat() * inner,
                    cx + cos(angle).toFloat() * outer,
                    cy + sin(angle).toFloat() * outer,
                    tick
                )
            }
            val labels = listOf("N", "E", "S", "W")
            for (i in labels.indices) {
                val angle = Math.toRadians((i * 90 - 90).toDouble())
                val x = cx + cos(angle).toFloat() * (radius - dp(35))
                val y = cy + sin(angle).toFloat() * (radius - dp(35)) + dp(6)
                textPaint.color = if (i == 0) Color.rgb(255, 91, 91) else Color.WHITE
                canvas.drawText(labels[i], x, y, textPaint)
            }

            canvas.save()
            canvas.rotate(-heading, cx, cy)
            val north = Path().apply {
                moveTo(cx, cy - radius * 0.68f)
                lineTo(cx - dp(13), cy)
                lineTo(cx, cy - dp(8))
                lineTo(cx + dp(13), cy)
                close()
            }
            val south = Path().apply {
                moveTo(cx, cy + radius * 0.68f)
                lineTo(cx - dp(13), cy)
                lineTo(cx, cy + dp(8))
                lineTo(cx + dp(13), cy)
                close()
            }
            canvas.drawPath(north, northPaint)
            canvas.drawPath(south, southPaint)
            canvas.restore()
            canvas.drawCircle(cx, cy, dp(5).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        }
    }
}
