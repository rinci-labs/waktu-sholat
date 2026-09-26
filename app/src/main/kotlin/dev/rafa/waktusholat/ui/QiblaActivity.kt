package dev.rafa.waktusholat.ui

import android.app.Activity
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.Qibla
import dev.rafa.waktusholat.data.ScheduleRepository

/**
 * Qibla compass.
 *
 * The heading comes from the rotation-vector sensor, which fuses accelerometer, gyroscope and
 * magnetometer and is the platform's recommended orientation source. It is registered in [onStart]
 * and unregistered in [onStop] so the sensor is never held while the screen is not showing, and
 * [SensorManager.SENSOR_DELAY_UI] is enough for a dial that only needs to look smooth.
 *
 * When the device has no rotation-vector sensor the screen still works: the numeric bearing and the
 * distance are shown without a live dial, which is the honest degradation rather than a stuck needle.
 */
class QiblaActivity : Activity(), SensorEventListener {

    private lateinit var repository: ScheduleRepository
    private lateinit var compass: QiblaCompassView
    private lateinit var bearingLabel: TextView
    private lateinit var distanceLabel: TextView
    private lateinit var hint: TextView
    private lateinit var locationLabel: TextView
    private lateinit var accuracyLabel: TextView

    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null

    /** Smoothed heading; the raw magnetometer jumps by several degrees between samples. */
    private var smoothedHeading: Float? = null
    private var currentAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qibla)
        setupTopBar(getString(R.string.qibla_title))

        repository = (application as WaktuSholatApp).repository

        compass = findViewById(R.id.compass)
        bearingLabel = findViewById(R.id.bearing)
        distanceLabel = findViewById(R.id.distance)
        hint = findViewById(R.id.hint)
        locationLabel = findViewById(R.id.location)
        accuracyLabel = findViewById(R.id.accuracy)

        findViewById<View>(R.id.change_location).setOnClickListener {
            startActivity(Intent(this, CityPickerActivity::class.java))
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor == null) {
            // No fused orientation sensor: keep the numbers, drop the live dial.
            hint.setText(R.string.qibla_no_sensor)
            compass.visibility = View.GONE
            accuracyLabel.visibility = View.GONE
        } else {
            accuracyLabel.text = accuracyText(currentAccuracy)
        }
    }

    override fun onStart() {
        super.onStart()
        // Bound here rather than in onCreate so a location changed from this screen shows on return.
        val city = repository.city
        val direction = Qibla.of(city.latitude, city.longitude)
        compass.qiblaDegrees = direction.bearingDegrees.toFloat()
        bearingLabel.text = direction.bearingText
        distanceLabel.text = direction.distanceText
        locationLabel.text = city.label

        val manager = sensorManager ?: return
        val sensor = rotationSensor ?: return
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onStop() {
        super.onStop()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        // Reused buffers: this runs tens of times a second, so it must not allocate.
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)

        // getOrientation reports azimuth counter-clockwise in radians, so negate into a clockwise
        // compass heading and normalise to 0..<360.
        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val heading = ((-azimuth % 360f) + 360f) % 360f

        // Exponential smoothing: enough to settle the needle, short enough to still feel live.
        val previous = smoothedHeading
        val next = if (previous == null) heading else previous + shortestDelta(previous, heading) * SMOOTHING
        smoothedHeading = (next + 360f) % 360f

        val shown = smoothedHeading ?: heading
        // Skip sub-pixel changes: a redraw costs more than the needle would visibly move.
        if (!compass.hasReading || kotlin.math.abs(shortestDelta(compass.headingDegrees, shown)) >= MIN_REDRAW_DEGREES) {
            compass.hasReading = true
            compass.headingDegrees = shown
        }
        if (hint.visibility != View.GONE) hint.visibility = View.GONE
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        currentAccuracy = accuracy
        if (rotationSensor != null) accuracyLabel.text = accuracyText(accuracy)
    }

    /** Signed shortest angle from [from] to [to], in `-180..180`, so the needle never spins the long way. */
    private fun shortestDelta(from: Float, to: Float): Float {
        val delta = (to - from + 540f) % 360f - 180f
        return delta
    }

    private fun accuracyText(accuracy: Int): String = getString(
        when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> R.string.qibla_accuracy_high
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> R.string.qibla_accuracy_medium
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> R.string.qibla_accuracy_low
            else -> R.string.qibla_accuracy_unreliable
        },
    )

    private companion object {
        /** Weight given to each new sample; 0.15 settles the dial without visible lag. */
        const val SMOOTHING = 0.15f

        /** Smallest heading change worth a redraw. */
        const val MIN_REDRAW_DEGREES = 0.4f
    }
}
