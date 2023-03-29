package com.gu.toolargetool

import android.app.Application
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import java.util.*

/**
 * A collection of helper methods to assist you in debugging crashes due to
 * [android.os.TransactionTooLargeException].
 *
 *
 * The easiest way to use this class is to call [.startLogging] in your app's
 * [Application.onCreate] method.
 */
object TooLargeTool {

    private var activityLogger: ActivitySavedStateLogger? = null

    @JvmStatic
    val isLogging: Boolean
        get() = activityLogger!!.isLogging

    /**
     * Helper method to print the result of [.bundleBreakdown] to ADB.
     *
     *
     * Logged at DEBUG priority.
     *
     * @param bundle to log the breakdown of
     * @param tag to log with
     */
    @JvmStatic
    fun logBundleBreakdown(tag: String, bundle: Bundle) {
        Log.println(Log.DEBUG, tag, bundleBreakdown(bundle))
    }

    /**
     * Helper method to print the result of [.bundleBreakdown] to ADB.
     *
     * @param bundle to log the breakdown of
     * @param tag to log with
     * @param priority to log with
     */
    @JvmStatic
    fun logBundleBreakdown(tag: String, priority: Int, bundle: Bundle) {
        Log.println(priority, tag, bundleBreakdown(bundle))
    }

    /**
     * Return a formatted String containing a breakdown of the contents of a [Bundle].
     *
     * @param bundle to format
     * @return a nicely formatted string (multi-line)
     */
    @JvmStatic
    fun bundleBreakdown(bundle: Bundle, depth: Int = 0): String {
         return sizeTreeStringfy( sizeTreeFromBundle(bundle, depth) )
    }

    fun sizeTreeStringfy( sizeTree: SizeTree, depth: Int = 0) : String {

        val padding = "   ".repeat(depth)

        var result = padding + bundleDescription( sizeTree ) +"\n"

        for ( subtree in sizeTree.subTrees) {

            if(subtree.subTrees.isEmpty())
                result += padding + " " + keyDescription(subtree) + "\n"
            else
                result += padding + " " + keyDescription(subtree) + "\n " + sizeTreeStringfy( subtree, depth + 1 ) + "\n"

        }

        return result
    }

    fun keyDescription( sizeTree: SizeTree, prefix: String = "* " ) : String {
        return String.format(
            Locale.UK,
            "%s%s = %,.1f KB",
            prefix, sizeTree.key, KB(sizeTree.totalSize)
        )
    }
    fun bundleDescription( sizeTree: SizeTree ) : String {

        val (key, totalSize, subTrees) = sizeTree

        return String.format(
            Locale.UK,
            "%s contains %d keys and measures %,.1f KB when serialized as a Parcel",
            key, subTrees.size, KB(totalSize)
        )
    }

    private fun KB(bytes: Int): Float {
        return bytes.toFloat() / 1000f
    }

    /**
     * Start logging information about all of the state saved by Activities and Fragments.
     *
     * @param application to log
     * @param priority to write log messages at
     * @param tag for log messages
     */
    @JvmOverloads
    @JvmStatic
    fun startLogging(application: Application, priority: Int = Log.DEBUG, tag: String = "TooLargeTool", depth: Int = -1) {
        startLogging(application, DefaultFormatter(depth), LogcatLogger(priority, tag))
    }

    @JvmStatic
    fun startLogging(application: Application, formatter: Formatter, logger: Logger) {
        if (activityLogger == null) {
            activityLogger = ActivitySavedStateLogger(formatter, logger, true)
        }

        if (activityLogger!!.isLogging) {
            return
        }

        activityLogger!!.startLogging()
        application.registerActivityLifecycleCallbacks(activityLogger)
    }

    /**
     * Stop all logging.
     *
     * @param application to stop logging
     */
    @JvmStatic
    fun stopLogging(application: Application) {
        if (!activityLogger!!.isLogging) {
            return
        }

        activityLogger!!.stopLogging()
        application.unregisterActivityLifecycleCallbacks(activityLogger)
    }
}

/**
 * Measure the sizes of all the values in a typed [Bundle] when written to a
 * [Parcel]. Returns a map from keys to the sizes, in bytes, of the associated values in
 * the Bundle.
 *
 * @param bundle to measure
 * @return a map from keys to value sizes in bytes
 */
fun sizeTreeFromBundle(bundle: Bundle, depth: Int = -1): SizeTree {

    if( depth == 0 ){
        return SizeTree("Bundle" + System.identityHashCode(bundle), sizeAsParcel(bundle), emptyList())
    }

    val results = ArrayList<SizeTree>(bundle.size())
    // We measure the totalSize of each value by measuring the total totalSize of the bundle before and
    // after removing that value and calculating the difference. We make a copy of the original
    // bundle so we can put all the original values back at the end. It's not possible to
    // carry out the measurements on the copy because of the way Android parcelables work
    // under the hood where certain objects are actually stored as references.
    val copy = Bundle(bundle)
    try {
        var bundleSize = sizeAsParcel(bundle)
        // Iterate over copy's keys because we're removing those of the original bundle
        for (key in copy.keySet()) {
            val value = bundle.getBundle(key)
            bundle.remove(key)
            val newBundleSize = sizeAsParcel(bundle)
            val valueSize = bundleSize - newBundleSize

            val subTree : List<SizeTree> = if( value != null ) sizeTreeFromBundle(value, depth - 1).subTrees else emptyList()

            results.add(SizeTree(key, valueSize, subTree))
            bundleSize = newBundleSize
        }
    } finally {
        // Put everything back into original bundle
        bundle.putAll(copy)
    }
    return SizeTree("Bundle" + System.identityHashCode(bundle), sizeAsParcel(bundle), results)
}

/**
 * Measure the size of a typed [Bundle] when written to a [Parcel].
 *
 * @param bundle to measure
 * @return size when written to parcel in bytes
 */
fun sizeAsParcel(bundle: Bundle): Int {
    val parcel = Parcel.obtain()
    try {
        parcel.writeBundle(bundle)
        return parcel.dataSize()
    } finally {
        parcel.recycle()
    }
}

/**
 * Measure the size of a [Parcelable] when written to a [Parcel].
 *
 * @param parcelable to measure
 * @return size of parcel in bytes
 */
fun sizeAsParcel(parcelable: Parcelable): Int {
    val parcel = Parcel.obtain()
    try {
        parcel.writeParcelable(parcelable, 0)
        return parcel.dataSize()
    } finally {
        parcel.recycle()
    }
}
