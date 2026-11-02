package io.github.ulope.karoostatusbartemp

import io.hammerhead.karooext.extension.KarooExtension

/**
 * Registers the app as a Karoo extension so it shows up in the Karoo extension list. The
 * temperature itself is drawn by [StatusBarTempService], which Android starts automatically
 * once the accessibility service has been enabled.
 */
class StatusBarTempExtension : KarooExtension("karoo-statusbar-temp", BuildConfig.VERSION_NAME)
