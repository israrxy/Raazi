package com.israrxy.raazi

/**
 * Launch bookkeeping that lives exactly as long as the app process.
 *
 * Android does not give an app a direct "was this a warm start?" signal:
 * `savedInstanceState` is also populated after process death, and the system
 * splash is shown for warm starts too (only hot starts skip it). A plain
 * object, however, is created with the process and destroyed with it, so
 * [hasCreatedUi] is a reliable cold-start check.
 *
 * Set it once the main activity has been created; every later launch in the
 * same process (returning from recents, notification, deep link, configuration
 * change) is then treated as warm and can skip the intro animation.
 */
internal object AppLaunchState {
    @Volatile
    var hasCreatedUi: Boolean = false
}
