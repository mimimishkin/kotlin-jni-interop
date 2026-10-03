package org.sample

import io.github.mimimishkin.jni.binding.annotation.JniExpects
import io.github.mimimishkin.jni.binding.annotation.LoadMethod
import java.util.ArrayList

/** Native resolves every type here itself with `FindClass`; none of it is bound by the plugin. */
@JniExpects
object AndroidApi {

    @LoadMethod
    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun load() {
        System.loadLibrary("example")
    }

    /** Appends [value] to [builder] [times] times through `StringBuilder.append`, and returns the result. */
    external fun appendTo(builder: StringBuilder, value: String, times: Int): String

    /** Adds [delta] to every entry of [map] and returns the new sum. */
    external fun bump(map: HashMap<String, Int>, delta: Int): Int

    /** Joins the elements of [parts] with [separator], reading the list with `ArrayList.size`/`get`. */
    external fun join(parts: ArrayList<String>, separator: String): String

    /** Calls `android.util.Log.i` and returns what it returned. */
    external fun logcat(tag: String, message: String): Int
}
