package com.gu.toolargetool

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

/**
 * Interface that allows flexibility in how TooLargeTool's output is formatted. The default
 * implementation [DefaultFormatter] should be suitable in most cases.
 */
interface Formatter {
    fun format(activity: Activity, bundle: Bundle): String
    fun format(fragmentManager: FragmentManager, fragment: Fragment, bundle: Bundle): String
}

/**
 * The default implementation of [Formatter].
 *
 * @author [@sfriedenberg](https://github.com/friedenberg)
 */
class DefaultFormatter(val depth: Int = -1): Formatter {
    override fun format(activity: Activity, bundle: Bundle): String {
        return activity.javaClass.simpleName + ".onSaveInstanceState wrote: " + TooLargeTool.bundleBreakdown(bundle, depth)
    }

    override fun format(fragmentManager: FragmentManager, fragment: Fragment, bundle: Bundle): String {
        var message = fragment.javaClass.simpleName + ".onSaveInstanceState wrote: " + TooLargeTool.bundleBreakdown(bundle, depth)
        val fragmentArguments = fragment.arguments
        if (fragmentArguments != null) {
            message += "\n* fragment arguments = " + TooLargeTool.bundleBreakdown(fragmentArguments)
        }

        return message
    }
}
