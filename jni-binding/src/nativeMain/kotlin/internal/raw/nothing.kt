package io.github.mimimishkin.jni.internal.raw

/**
 * Dokka does not include the Cinterop package. This property is a workaround to make this package not empty to at least
 * document it.
 */
private inline val Nothing.nothing: Nothing get() = throw Error()