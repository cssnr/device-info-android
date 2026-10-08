package org.cssnr.deviceinfo.data

import android.app.LocaleManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Display
import androidx.annotation.StringRes
import androidx.core.net.toUri
import org.cssnr.deviceinfo.R
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * What a row shows on its right-hand side.
 *
 * [Text] is a fact the collector read: a `Build` field, a settings lookup, a formatted date. Those
 * are never localized, because they are values rather than words.
 *
 * [Resource] is the small closed set of words that have to follow the device locale, currently only
 * "Yes", "No" and "N/A". It is a resource rather than a string for the same reason labels are: a
 * locale change recreates the Activity but not the ViewModel, so a word resolved at collect time
 * would keep rendering in the old language until the process died.
 *
 * Modelling this as two alternatives rather than a string field with a nullable resource beside it is
 * what stops a row from carrying both, and every reader having to work out which one wins.
 */
sealed interface InfoValue {

    /** A collected fact, shown verbatim. */
    data class Text(val value: String) : InfoValue

    /** A word to localize at draw time, for the values that are not facts at all. */
    data class Resource(@StringRes val res: Int) : InfoValue
}

/**
 * One read-only key/value row on the Info screen.
 *
 * [id] is the row's stable identity, independent of its value. Selection is tracked by id, so a
 * row stays selected when the value it holds changes underneath it.
 */
data class InfoItem(
    val id: String,
    @StringRes val labelRes: Int,
    val value: InfoValue,
)

/** A titled run of [InfoItem]s, drawn as a single rounded group. */
data class InfoCategory(
    val id: String,
    @StringRes val titleRes: Int,
    val items: List<InfoItem>,
)

/**
 * Reads device and OS facts off `android.os.Build` plus the handful of framework services that
 * only a [Context] can reach.
 *
 * [context] is expected to be an application context. Everything here is device state that is fixed
 * for the life of the process, so there is nothing an Activity-scoped context would add, and this
 * object outlives every screen anyway.
 *
 * Most fields read here are a `static final` initialised in `Build`'s class initialiser from a
 * `SystemProperties` native call, which the framework runs exactly once per process on first
 * touch of any `Build` member. After that the fields are plain reads with no cost worth avoiding.
 * That is why this collector keeps no cache of its own: the snapshot it returns is held by the
 * ViewModel as state, so recomputing it is a few dozen field reads and nothing more. The one-time
 * class initialiser happens whether or not this runs.
 *
 * Values are collected once and left immutable. They only change across a reboot or an OS update,
 * both of which restart the process, so there is nothing for the Info screen to reload. Volatile
 * categories (memory, storage, battery, uptime) would change that, and would want the same
 * recollection seam this collector already leaves open by taking no cache of its own.
 *
 * Labels and the few localized values stay `@StringRes` rather than resolved strings even though a
 * `Context` is now available. A locale change recreates the Activity but not the ViewModel, so
 * text baked into a value at collect time would keep rendering in the old language until the
 * process died. The UI resolves every label and every localized value during composition, where a
 * locale change reaches it.
 *
 * The four groups are cut by what a fact is about rather than by where the field lives in the
 * framework, because the copy actions are per group and a report is only useful if selecting one
 * group selects one kind of thing. `Build` fields are therefore spread across `device` and `os`
 * rather than kept in a group of their own:
 * - `device` is the physical hardware: who made it, what board it is, what chip is in it.
 * - `display` is the physical screen: its native resolution, density and refresh capabilities.
 * - `os` is the software build: the Android version, the build that identifies it, the runtime it
 *   runs on.
 * - `system` is everything the platform does rather than is: the boot chain, the Google components
 *   shipped on top of it, the names software uses to tell this device apart from another one, and
 *   the state of its integrity. Those last two are thin enough that a header each would be more
 *   chrome than content.
 */
object DeviceInfoCollector {

    fun collect(context: Context): List<InfoCategory> = listOf(
        osCategory(context),
        deviceCategory(),
        displayCategory(context),
        systemCategory(context),
    )

    private fun osCategory(context: Context): InfoCategory = InfoCategory(
        id = "os",
        titleRes = R.string.info_group_os,
        items = buildList {
            item("os.release", R.string.info_os_release, Build.VERSION.RELEASE)
            item("os.sdk", R.string.info_os_sdk, Build.VERSION.SDK_INT.toString())
            // Zero means the device is running a final release, so there is nothing to show.
            if (Build.VERSION.PREVIEW_SDK_INT != 0) {
                item(
                    "os.preview_sdk",
                    R.string.info_os_preview_sdk,
                    Build.VERSION.PREVIEW_SDK_INT.toString(),
                )
            }
            item("os.codename", R.string.info_os_codename, Build.VERSION.CODENAME)
            item("os.base_os", R.string.info_os_base_os, Build.VERSION.BASE_OS)
            item("os.incremental", R.string.info_os_incremental, Build.VERSION.INCREMENTAL)
            item("os.security_patch", R.string.info_os_security_patch, Build.VERSION.SECURITY_PATCH)
            // Added in API 31, and zero on a device that has not declared conformance to a media
            // performance class, so the zero is a "no answer" rather than a class of its own.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                Build.VERSION.MEDIA_PERFORMANCE_CLASS != 0
            ) {
                item(
                    "os.media_performance_class",
                    R.string.info_os_media_performance_class,
                    Build.VERSION.MEDIA_PERFORMANCE_CLASS.toString(),
                )
            }
            // ART fills this from the `release` field of uname(2), so it is the running kernel
            // rather than the Android build, and it is already a string with no exec to spawn.
            item("os.kernel", R.string.info_os_kernel, property("os.version"))
            // java.vm.name is "ART" or "Dalvik", which is the answer worth having. java.vm.version
            // is ART's own "0.9" on every ART device, so it is not worth a row.
            item("os.java_vm", R.string.info_os_java_vm, property("java.vm.name"))
            item("os.language", R.string.info_os_language, systemLanguage(context))
            item("os.time_zone", R.string.info_os_time_zone, TimeZone.getDefault().id)
            // The rest of the group is what names this particular build, which is the other half of
            // what an Android version is. Build.ID is the short revision, Build.DISPLAY the longer
            // platform-suffixed one that Android's own About screen shows.
            item("os.build_id", R.string.info_os_build_id, Build.ID)
            item("os.build_number", R.string.info_os_build_number, Build.DISPLAY)
            item("os.fingerprint", R.string.info_os_fingerprint, Build.FINGERPRINT)
            item("os.build_type", R.string.info_os_build_type, Build.TYPE)
            item("os.build_tags", R.string.info_os_build_tags, Build.TAGS)
            item("os.build_host", R.string.info_os_build_host, Build.HOST)
            // Zero means the platform never stamped a build time; formatting it would render the
            // Unix epoch as though it were a real date.
            if (Build.TIME > 0L) {
                item("os.build_time", R.string.info_os_build_time, formatBuildTime(Build.TIME))
            }
        },
    )

    private fun deviceCategory(): InfoCategory = InfoCategory(
        id = "device",
        titleRes = R.string.info_group_device,
        items = buildList {
            item("device.manufacturer", R.string.info_device_manufacturer, Build.MANUFACTURER)
            item("device.brand", R.string.info_device_brand, Build.BRAND)
            item("device.model", R.string.info_device_model, Build.MODEL)
            item("device.device", R.string.info_device_device, Build.DEVICE)
            item("device.product", R.string.info_device_product, Build.PRODUCT)
            item("device.hardware", R.string.info_device_hardware, Build.HARDWARE)
            item("device.board", R.string.info_device_board, Build.BOARD)
            // SOC_MODEL and SOC_MANUFACTURER only became public in API 31. Below that they are
            // hidden, and the usual fallback (/proc/cpuinfo's Hardware line) is absent on plenty of
            // kernels, so the row is omitted rather than filled with a guess. Both halves are
            // filtered separately because each is independently allowed to be absent.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val soc = listOfNotNull(
                    Build.SOC_MANUFACTURER.takeIf { it.isKnown() },
                    Build.SOC_MODEL.takeIf { it.isKnown() },
                ).joinToString(" ")
                item("device.soc", R.string.info_device_soc, soc)
            }
            // Also from uname(2), this time the `machine` field. It is the kernel's own word for the
            // CPU, which is not always the ABI word: SUPPORTED_ABIS below says `arm64-v8a` where
            // this says `aarch64`.
            item("device.architecture", R.string.info_device_architecture, property("os.arch"))
            item("device.abis", R.string.info_device_abis, Build.SUPPORTED_ABIS.joinToString())
            // Split by width as well, because "does this device run 64-bit code" is the question a
            // reader actually has of an ABI list, and the two arrays are empty rather than absent
            // on a device that is purely one width.
            item(
                "device.abis_32",
                R.string.info_device_abis_32,
                Build.SUPPORTED_32_BIT_ABIS.joinToString()
            )
            item(
                "device.abis_64",
                R.string.info_device_abis_64,
                Build.SUPPORTED_64_BIT_ABIS.joinToString()
            )
            // Logical processors, which is what the runtime schedules onto and therefore what the
            // core count means everywhere else in Android. It is not a physical core count.
            item(
                "device.cpu_cores",
                R.string.info_device_cpu_cores,
                Runtime.getRuntime().availableProcessors().toString(),
            )
            // Null on a device with no radio, which the blank check below drops. Not Build.RADIO,
            // which was deprecated in API 15 because it is read too early to be reliable.
            item("device.radio", R.string.info_device_radio, Build.getRadioVersion())
        },
    )

    /**
     * The physical screen, read off the default [Display] plus `DisplayMetrics`.
     *
     * `resolution` is the panel's native size from the active `Display.Mode`; `absolute_resolution`
     * is the app-visible size from `DisplayMetrics`, which is smaller once decor, cutouts and
     * compatibility scaling are subtracted. Brightness, brightness mode and orientation are
     * deliberately left out: they are momentary state rather than device facts, and this snapshot
     * is collected once per process.
     *
     * The [Display] comes from `DisplayManager.getDisplay(Display.DEFAULT_DISPLAY)` rather than a
     * window, so an application context is enough and there is nothing an Activity would add. When
     * the display is absent the mode rows are omitted and the `DisplayMetrics` rows still show.
     */
    private fun displayCategory(context: Context): InfoCategory = InfoCategory(
        id = "display",
        titleRes = R.string.info_group_display,
        items = buildList {
            val metrics = context.resources.displayMetrics
            val display = primaryDisplay(context)
            val mode = display?.mode

            if (mode != null) {
                item(
                    "display.resolution",
                    R.string.info_display_resolution,
                    "${mode.physicalWidth} x ${mode.physicalHeight}",
                )
            }
            item(
                "display.absolute_resolution",
                R.string.info_display_absolute_resolution,
                "${metrics.widthPixels} x ${metrics.heightPixels}",
            )
            if (mode != null) {
                item(
                    "display.aspect_ratio",
                    R.string.info_display_aspect_ratio,
                    aspectRatio(mode.physicalWidth, mode.physicalHeight),
                )
                item(
                    "display.screen_size",
                    R.string.info_display_screen_size,
                    screenSizeInches(mode.physicalWidth, mode.physicalHeight, metrics.xdpi),
                )
            }
            item(
                "display.screen_density",
                R.string.info_display_screen_density,
                metrics.xdpi.takeIf { it > 0f }?.let { "${it.toInt()} ppi" },
            )
            item(
                "display.density_dpi",
                R.string.info_display_density_dpi,
                "${metrics.densityDpi} dpi",
            )
            item(
                "display.density",
                R.string.info_display_density,
                metrics.density.toString(),
            )
            if (mode != null) {
                item(
                    "display.refresh_rate",
                    R.string.info_display_refresh_rate,
                    "${Math.round(mode.refreshRate)} Hz",
                )
            }
            val supportedRates = display?.supportedModes
                ?.map { Math.round(it.refreshRate) }
                ?.distinct()
                ?.sorted()
                ?.joinToString(separator = " Hz, ", postfix = " Hz") { it.toString() }
            item(
                "display.supported_refresh_rates",
                R.string.info_display_supported_refresh_rates,
                supportedRates,
            )
            if (display != null) {
                add(
                    InfoItem(
                        "display.hdr",
                        R.string.info_display_hdr,
                        InfoValue.Resource(if (display.isHdr) R.string.info_yes else R.string.info_no),
                    ),
                )
                item("display.hdr_types", R.string.info_display_hdr_types, hdrTypes(display))
                add(
                    InfoItem(
                        "display.wide_color_gamut",
                        R.string.info_display_wide_color_gamut,
                        InfoValue.Resource(if (display.isWideColorGamut) R.string.info_yes else R.string.info_no),
                    ),
                )
            }
        },
    )

    private fun systemCategory(context: Context): InfoCategory = InfoCategory(
        id = "system",
        titleRes = R.string.info_group_system,
        items = buildList {
            item("system.bootloader", R.string.info_system_bootloader, Build.BOOTLOADER)
            item(
                "system.play_services",
                R.string.info_system_play_services,
                packageVersion(context, PLAY_SERVICES_PACKAGE),
            )
            item(
                "system.play_system_update",
                R.string.info_system_play_system_update,
                mainlineVersion(context),
            )
            // Deliberately not read. Build.SERIAL is deprecated and hardcoded in AOSP to a property
            // that does not exist, so it always reads "unknown"; Build.getSerial() needs
            // READ_PRIVILEGED_PHONE_STATE and throws for any normal app at this target SDK. There is
            // no honest serial to show, so the row says so instead of showing a placeholder value
            // that looks like data.
            add(
                InfoItem(
                    "system.serial",
                    R.string.info_system_serial,
                    InfoValue.Resource(R.string.info_unavailable)
                ),
            )
            item("system.android_id", R.string.info_system_android_id, androidId(context))
            item("system.gsf_id", R.string.info_system_gsf_id, gsfId(context))
            // Both signals are heuristics and either can miss. A Magisk-style root hides itself from
            // a file probe, and a device can be rooted with release-keys signed. Treat this as a
            // hint, not a verdict.
            add(
                InfoItem(
                    "system.rooted",
                    R.string.info_system_rooted,
                    InfoValue.Resource(if (isRooted()) R.string.info_yes else R.string.info_no),
                ),
            )
        },
    )

    /**
     * Drops rows whose value the platform left blank or replaced with the literal "unknown", which
     * is what `Build` and `Build.VERSION` return for a field they have no value for. A row reading
     * "unknown" for a value that is simply absent is noise, so an unreadable field is left out
     * rather than shown as missing.
     */
    private fun MutableList<InfoItem>.item(
        id: String,
        @StringRes labelRes: Int,
        value: String?,
    ) {
        if (value != null && value.isKnown()) {
            add(InfoItem(id, labelRes, InfoValue.Text(value)))
        }
    }

    private fun String.isKnown(): Boolean =
        isNotBlank() && !equals(Build.UNKNOWN, ignoreCase = true)

    private fun primaryDisplay(context: Context): Display? =
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)

    /** Reduced `width:height` pair, e.g. 1080x2400 becomes 9:20. */
    private fun aspectRatio(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        val divisor = gcd(width, height)
        if (divisor <= 0) return null
        return "${width / divisor}:${height / divisor}"
    }

    /** Diagonal panel size from the native resolution and the physical x density. */
    private fun screenSizeInches(width: Int, height: Int, xdpi: Float): String? {
        if (width <= 0 || height <= 0 || xdpi <= 0f) return null
        val w = width / xdpi.toDouble()
        val h = height / xdpi.toDouble()
        return String.format(Locale.US, "%.2f inches", Math.sqrt(w * w + h * h))
    }

    /**
     * Human names for the display's HDR types, or null when it reports none.
     *
     * Matched on `HdrCapabilities` constants rather than raw ints. An unrecognized type is
     * dropped; a display advertising only those reads as having no known HDR.
     *
     * Still read off `HdrCapabilities` rather than `Mode.getSupportedHdrTypes()`: that
     * replacement needs API 34 and this collector starts at 26.
     */
    @Suppress("DEPRECATION")
    private fun hdrTypes(display: Display): String? {
        val supported = display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
        val types = mutableListOf<String>()
        for (type in supported) {
            when (type) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> types.add("Dolby Vision")
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> types.add("HDR10")
                Display.HdrCapabilities.HDR_TYPE_HLG -> types.add("Hybrid Log-Gamma")
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> types.add("HDR10+")
            }
        }
        return types.takeIf { it.isNotEmpty() }?.joinToString()
    }

    private fun property(key: String): String? = System.getProperty(key)

    /**
     * The device's language rather than this app's.
     *
     * `Locale.getDefault()` follows a per-app language override on Android 13+, so on a phone set
     * to Dutch with this app forced to English it would report English and be wrong about the
     * device. `LocaleManager.getSystemLocales()` is documented as returning the system locales
     * while ignoring app-specific overrides. Below API 33 the per-app feature does not exist, so
     * the default locale is the system locale.
     *
     * `Resources.getSystem()` is not an alternative: it returns the same overridden locale, which
     * is the trap this replaces.
     */
    private fun systemLanguage(context: Context): String {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.systemLocales?.get(0)
        } else {
            Locale.getDefault()
        }
        return locale?.let { "${it.displayLanguage} (${it.toLanguageTag()})" }.orEmpty()
    }

    /**
     * The version of an installed package, or null when it is not installed.
     *
     * Package visibility filtering applies to [PackageManager.getPackageInfo] from API 30, so an
     * app that is not declared in the manifest's `<queries>` is reported as not installed however
     * it is actually installed. Every package read here is declared there.
     */
    private fun packageVersion(context: Context, packageName: String): String? = try {
        context.packageManager.getPackageInfo(packageName, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * The Google Play system update version, which is the version of the mainline module metadata
     * package. Google ships that as `com.google.android.modulemetadata` and AOSP as
     * `com.android.modulemetadata`, so both are tried. Mainline modules exist from API 29.
     */
    private fun mainlineVersion(context: Context): String? =
        MODULE_METADATA_PACKAGES.firstNotNullOfOrNull { packageVersion(context, it) }

    /**
     * The Android ID, which since Android 8 is scoped to the combination of this app's signing
     * key, the user, and the device. It is not a device-wide identifier and two apps on one phone
     * will not agree on it.
     */
    private fun androidId(context: Context): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    /**
     * The Google Services Framework ID, the account-scoped device ID Google Play services keeps.
     * It lives in the gservices provider rather than in [Settings.Secure], and reading it needs
     * `READ_GSERVICES`, which is a normal protection level permission and so is granted at install
     * from the manifest entry. Absent on a device with no Play services, hence the catch.
     */
    private fun gsfId(context: Context): String? = try {
        context.contentResolver
            .query(GSERVICES_URI, null, GSERVICES_SELECTION, arrayOf(GSERVICES_ANDROID_ID), null)
            ?.use { cursor ->
                // The value is stored as a decimal string but is conventionally displayed as hex,
                // which is also the form every other device-info tool shows.
                val valueColumn = cursor.getColumnIndex(GSERVICES_VALUE)
                if (cursor.moveToFirst() && valueColumn >= 0) {
                    cursor.getString(valueColumn).toLongOrNull()?.toString(HEX_RADIX)
                } else {
                    null
                }
            }
    } catch (_: Exception) {
        // A SecurityException if the permission is missing, but also whatever a vendor's provider
        // does with an unexpected query. Either way there is no ID to show.
        null
    }

    /**
     * A rooted device shows one of two traces: a build signed with `test-keys`, or a `su` binary on
     * disk. This is the filesystem probe every device-info app uses, and it is the only part of
     * collection that touches disk.
     */
    private fun isRooted(): Boolean =
        Build.TAGS?.contains(TEST_KEYS_TAG) == true || SU_PATHS.any { File(it).exists() }

    private fun formatBuildTime(epochMillis: Long): String =
        DateFormat.getDateTimeInstance().format(Date(epochMillis))

    private const val TEST_KEYS_TAG = "test-keys"
    private const val HEX_RADIX = 16

    private const val PLAY_SERVICES_PACKAGE = "com.google.android.gms"

    private val MODULE_METADATA_PACKAGES =
        listOf("com.google.android.modulemetadata", "com.android.modulemetadata")

    private val GSERVICES_URI: Uri = "content://com.google.android.gsf.gservices".toUri()
    private const val GSERVICES_SELECTION = "name = ?"
    private const val GSERVICES_ANDROID_ID = "android_id"
    private const val GSERVICES_VALUE = "value"

    /**
     * The paths a `su` binary has historically been installed to. There is no complete list; a
     * rooted device with an unusual path is missed, which is why this is only ever a hint.
     */
    private val SU_PATHS = listOf(
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/system/sbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/data/adb/magisk",
    )
}
