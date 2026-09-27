package dev.rafa.waktusholat.ui

import android.content.Intent
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.View
import android.widget.TextView
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.WaktuSholatApp
import dev.rafa.waktusholat.core.Qibla
import dev.rafa.waktusholat.data.ScheduleRepository
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Qibla compass, built to be right rather than merely smooth.
 *
 * - **True north.** Phone sensors measure *magnetic* north, but the Qibla bearing is from *true*
 *   north. The local magnetic declination ([GeomagneticField], the World Magnetic Model) is added
 *   to every heading; it is about 1° in Java, 3–4° in Makkah and over 10° in parts of the world.
 * - **Calibration first.** The dial stays hidden behind a figure-8 calibration card until the
 *   magnetometer reports medium or high accuracy; it comes back whenever accuracy drops.
 * - **Honest warnings.** A field strength outside the Earth's normal range means metal or a magnet
 *   nearby; a tilted phone gives an unreliable azimuth. Both are shown instead of a wrong needle.
 * - **Sensor fallback.** Rotation vector (accelerometer + gyroscope + magnetometer), then the
 *   geomagnetic rotation vector (no gyroscope), then raw accelerometer + magnetometer.
 *
 * Sensors are held only between [onStart] and [onStop]; the rotation matrix is remapped to the
 * current display rotation, so the dial is right in any orientation.
 */
class QiblaActivity : BaseActivity(), SensorEventListener {

    private lateinit var repository: ScheduleRepository
    private lateinit var compass: QiblaCompassView
    private lateinit var calibration: View
    private lateinit var hint: TextView
    private lateinit var accuracyLabel: TextView
    private lateinit var warning: TextView

    private var sensorManager: SensorManager? = null
    private var orientationSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var haveGravity = false
    private var haveGeomagnetic = false

    /** Magnetic declination at the selected location, degrees east of true north. */
    private var declination = 0f
    private var smoothedHeading: Float? = null
    private var magneticAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var calibrated = false
    private var skipped = false
    private var wasAligned = false
    private var recalibrateUntil = 0L
    private var interference = false
    private var tilted = false

    private val handler = Handler(Looper.getMainLooper())
    private val showSkip = Runnable { findViewById<View>(R.id.calibration_skip).visibility = View.VISIBLE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qibla)
        setupTopBar(getString(R.string.qibla_title))

        repository = (application as WaktuSholatApp).repository
        compass = findViewById(R.id.compass)
        calibration = findViewById(R.id.calibration)
        hint = findViewById(R.id.hint)
        accuracyLabel = findViewById(R.id.accuracy)
        warning = findViewById(R.id.warning)

        findViewById<View>(R.id.change_location).setOnClickListener {
            startActivity(Intent(this, CityPickerActivity::class.java))
        }
        findViewById<View>(R.id.calibration_skip).setOnClickListener {
            skipped = true
            updateCalibration()
        }
        findViewById<View>(R.id.recalibrate).setOnClickListener {
            skipped = false
            calibrated = false
            recalibrateUntil = System.currentTimeMillis() + MIN_CALIBRATION_MILLIS
            handler.postDelayed({ if (magneticAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) onAccuracyChanged(magnetometer, magneticAccuracy) }, MIN_CALIBRATION_MILLIS + 100)
            updateCalibration()
        }

        val manager = getSystemService(SENSOR_SERVICE) as? SensorManager
        sensorManager = manager
        orientationSensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (magnetometer == null || (orientationSensor == null && accelerometer == null)) {
            // No compass hardware: keep the numbers, drop the live dial and the calibration.
            hint.setText(R.string.qibla_no_sensor)
            compass.visibility = View.GONE
            calibration.visibility = View.GONE
            accuracyLabel.visibility = View.GONE
            findViewById<View>(R.id.recalibrate).visibility = View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        // Bound here so a location changed from this screen shows on return.
        val city = repository.city
        val direction = Qibla.of(city.latitude, city.longitude)
        compass.qiblaDegrees = direction.bearingDegrees.toFloat()
        findViewById<TextView>(R.id.bearing).text = direction.bearingText
        findViewById<TextView>(R.id.distance).text = direction.distanceText
        findViewById<TextView>(R.id.location).text = city.label
        declination = GeomagneticField(
            city.latitude.toFloat(), city.longitude.toFloat(), 0f, System.currentTimeMillis(),
        ).declination
        findViewById<TextView>(R.id.declination).text =
            getString(R.string.qibla_declination, String.format(Locale.getDefault(), "%+.1f°", declination))

        val manager = sensorManager ?: return
        val mag = magnetometer ?: return
        // The magnetometer is always registered: its accuracy drives calibration and its field
        // strength reveals interference, even when a fused sensor supplies the heading.
        manager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
        val fused = orientationSensor
        if (fused != null) {
            manager.registerListener(this, fused, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometer?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
        handler.postDelayed(showSkip, SKIP_DELAY_MILLIS)
        updateCalibration()
    }

    override fun onStop() {
        super.onStop()
        sensorManager?.unregisterListener(this)
        handler.removeCallbacks(showSkip)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lowPass(event.values, geomagnetic, haveGeomagnetic)
                haveGeomagnetic = true
                val v = event.values
                val strength = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                setInterference(strength < MIN_FIELD_UT || strength > MAX_FIELD_UT)
                if (orientationSensor == null && haveGravity) {
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) onRotation()
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lowPass(event.values, gravity, haveGravity)
                haveGravity = true
            }
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                onRotation()
            }
        }
    }

    /** Turns the current rotation matrix into a true-north heading and updates the dial. */
    private fun onRotation() {
        // Sensor axes are the device's natural (portrait) axes; remap them to the current display
        // rotation so the heading is where the top of the *screen* points.
        val matrix = when (displayRotation()) {
            Surface.ROTATION_90 -> remapped(SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X)
            Surface.ROTATION_180 -> remapped(SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y)
            Surface.ROTATION_270 -> remapped(SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X)
            else -> rotationMatrix
        }
        SensorManager.getOrientation(matrix, orientation)
        val pitch = Math.toDegrees(orientation[1].toDouble())
        val roll = Math.toDegrees(orientation[2].toDouble())
        setTilted(abs(pitch) > MAX_TILT_DEGREES || abs(roll) > MAX_TILT_DEGREES)

        // Azimuth is magnetic, clockwise from north in radians; add the declination for true north.
        val magnetic = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val heading = ((magnetic + declination) % 360f + 360f) % 360f

        val previous = smoothedHeading
        val next = if (previous == null) heading else previous + shortestDelta(previous, heading) * SMOOTHING
        val shown = (next + 360f) % 360f
        smoothedHeading = shown

        if (!compass.hasReading || abs(shortestDelta(compass.headingDegrees, shown)) >= MIN_REDRAW_DEGREES) {
            compass.hasReading = !tilted
            compass.headingDegrees = shown
        }

        // One tap of haptics on entering alignment, so the user can find it without staring.
        val aligned = calibrated && !tilted && abs(shortestDelta(shown, compass.qiblaDegrees)) <= ALIGNED_DEGREES
        if (aligned && !wasAligned) {
            compass.performHapticFeedback(
                if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
            )
        }
        if (aligned != wasAligned) {
            hint.setText(if (aligned) R.string.qibla_aligned else R.string.qibla_hint)
            hint.setTextColor(getColor(if (aligned) R.color.brand else R.color.text_primary))
        }
        wasAligned = aligned
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type != Sensor.TYPE_MAGNETIC_FIELD) return
        magneticAccuracy = accuracy
        // After "Calibrate again" the card stays up until the user has actually moved the phone:
        // a fresh report of good accuracy, not the one already cached before the tap.
        if (accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM && System.currentTimeMillis() > recalibrateUntil) calibrated = true
        if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW && calibrated && !skipped) calibrated = false
        updateCalibration()
    }

    /** Shows the calibration card until the magnetometer is trustworthy (or the user skips). */
    private fun updateCalibration() {
        if (magnetometer == null) return
        val needed = !calibrated && !skipped
        calibration.animate().cancel()
        if (needed) {
            calibration.visibility = View.VISIBLE
            calibration.alpha = 1f
            compass.alpha = 0.15f
        } else if (calibration.visibility == View.VISIBLE) {
            calibration.animate().alpha(0f).setDuration(250).withEndAction {
                calibration.visibility = View.GONE
                refreshWarning()
            }.start()
            compass.animate().alpha(1f).setDuration(250).start()
        }
        accuracyLabel.text = getString(
            when (magneticAccuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> R.string.qibla_accuracy_high
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> R.string.qibla_accuracy_medium
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> R.string.qibla_accuracy_low
                else -> R.string.qibla_accuracy_unreliable
            },
        )
        hint.visibility = if (needed) View.GONE else View.VISIBLE
        refreshWarning()
    }

    private fun setInterference(value: Boolean) {
        if (value == interference) return
        interference = value
        refreshWarning()
    }

    private fun setTilted(value: Boolean) {
        if (value == tilted) return
        tilted = value
        refreshWarning()
    }

    /** One line, most important problem first. */
    private fun refreshWarning() {
        val message = when {
            interference -> R.string.qibla_interference
            tilted && calibration.visibility != View.VISIBLE -> R.string.qibla_hold_flat
            else -> 0
        }
        warning.visibility = if (message == 0) View.GONE else View.VISIBLE
        if (message != 0) warning.setText(message)
    }

    private fun remapped(x: Int, y: Int): FloatArray {
        SensorManager.remapCoordinateSystem(rotationMatrix, x, y, remappedMatrix)
        return remappedMatrix
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        if (android.os.Build.VERSION.SDK_INT >= 30) display?.rotation ?: Surface.ROTATION_0 else windowManager.defaultDisplay.rotation

    private fun lowPass(input: FloatArray, output: FloatArray, initialised: Boolean) {
        for (i in 0 until 3) output[i] = if (initialised) output[i] + LOW_PASS * (input[i] - output[i]) else input[i]
    }

    /** Signed shortest angle from [from] to [to], in `-180..180`, so the needle never spins the long way. */
    private fun shortestDelta(from: Float, to: Float): Float = (to - from + 540f) % 360f - 180f

    private companion object {
        /** Weight given to each new heading; settles the dial without visible lag. */
        const val SMOOTHING = 0.15f
        const val LOW_PASS = 0.2f
        const val MIN_REDRAW_DEGREES = 0.4f

        /** Matches the dial's own "aligned" highlight. */
        const val ALIGNED_DEGREES = 3f

        /** Beyond this pitch or roll the azimuth is unreliable; the phone should lie flat. */
        const val MAX_TILT_DEGREES = 30.0

        /** The Earth's field is roughly 25–65 µT everywhere; outside that, something is interfering. */
        const val MIN_FIELD_UT = 20f
        const val MAX_FIELD_UT = 75f

        const val SKIP_DELAY_MILLIS = 4_000L

        /** Minimum figure-8 time after "Calibrate again", even if the sensor already reports well. */
        const val MIN_CALIBRATION_MILLIS = 6_000L
    }
}
