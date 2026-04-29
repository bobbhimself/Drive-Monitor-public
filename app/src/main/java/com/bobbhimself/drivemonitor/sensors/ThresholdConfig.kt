package com.bobbhimself.drivemonitor.sensors

object ThresholdConfig {

    // --- Caution thresholds (unsigned G magnitudes) ---
    const val ACCELERATION_CAUTION_G = 0.24f  // THR-004: revised from 0.28f after road session
    const val BRAKING_CAUTION_G      = 0.13f  // THR-002: revised from 0.29f after parking lot session
    const val TURNING_CAUTION_G      = 0.26f  // THR-003: revised from 0.28f after parking lot session

    // --- Alert thresholds (unsigned G magnitudes) ---
    const val ACCELERATION_ALERT_G = 0.31f  // THR-004: revised from 0.35f after road session
    const val BRAKING_ALERT_G      = 0.26f  // THR-002: revised from 0.36f after parking lot session
    const val TURNING_ALERT_G      = 0.35f

    // --- Bump rejection ---
    const val BUMP_VERTICAL_THRESHOLD_G = 0.40f

    // --- Processing constants ---
    const val EMA_ALPHA  = 0.20f
    const val DEADBAND_G = 0.03f

    // --- Calibration constants ---
    const val CALIBRATION_DURATION_MS       = 1000L
    const val CALIBRATION_MIN_SAMPLES       = 20
    const val CALIBRATION_STABILITY_RMS_G   = 0.02f
    const val CALIBRATION_MAX_DEVIATION_G   = 0.05f

    // --- Lifecycle timing constants (monotonic milliseconds) ---
    const val PERSISTENCE_WINDOW_MS = 200L
    const val QUIET_WINDOW_MS       = 750L
    const val COOLDOWN_WINDOW_MS    = 2000L
}
