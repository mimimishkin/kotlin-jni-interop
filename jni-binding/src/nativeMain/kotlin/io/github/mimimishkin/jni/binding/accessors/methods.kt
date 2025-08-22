@file:Suppress("NOTHING_TO_INLINE")

package io.github.mimimishkin.jni.binding.accessors

import io.github.mimimishkin.jni.binding.*
import kotlinx.cinterop.NativePlacement

/**
 * Unifies usage of `call<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` and returns the result.
 *
 * Note: If the return type is a primitive type, it cannot be nullable, otherwise it must be nullable.
 */
public inline fun <reified R> JMethodID.asMethod(): context(JniEnv, NativePlacement) JObject.() -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(): R {
        return callMethod<R>(this@asMethod, jArgs(0) {})
    }
}

/**
 * Unifies usage of `call<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified R> JMethodID.asMethod(): context(JniEnv, NativePlacement) JObject.(P1) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(p1): R {
        return callMethod<R>(this@asMethod, jArgs(1) { any(p1) })
    }
}

/**
 * Unifies usage of `call<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified R> JMethodID.asMethod(): context(JniEnv, NativePlacement) JObject.(P1, P2) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(p1, p2): R {
        return callMethod<R>(this@asMethod, jArgs(2) { any(p1); any(p2) })
    }
}

/**
 * Unifies usage of `call<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified R> JMethodID.asMethod(): context(JniEnv, NativePlacement) JObject.(P1, P2, P3) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(p1, p2, p3): R {
        return callMethod<R>(this@asMethod, jArgs(3) { any(p1); any(p2); any(p3) })
    }
}

/**
 * Unifies usage of `call<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified R> JMethodID.asMethod(): context(JniEnv, NativePlacement) JObject.(P1, P2, P3, P4) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(p1, p2, p3, p4): R {
        return callMethod<R>(this@asMethod, jArgs(4) { any(p1); any(p2); any(p3); any(p4) })
    }
}

/**
 * Unifies usage of `call<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified R> JMethodID.asMethod(): context(JniEnv, NativePlacement) JObject.(P1, P2, P3, P4, P5) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(p1, p2, p3, p4, p5): R {
        return callMethod<R>(this@asMethod, jArgs(5) { any(p1); any(p2); any(p3); any(p4); any(p5) })
    }
}

/**
 * Unifies usage of `call<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified R> JMethodID.asMethod(): context(JniEnv, NativePlacement) JObject.(P1, P2, P3, P4, P5, P6) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(p1, p2, p3, p4, p5, p6): R {
        return callMethod<R>(this@asMethod, jArgs(6) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6) })
    }
}

/**
 * Unifies usage of `call<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified R> JMethodID.asMethod(): context(JniEnv, NativePlacement) JObject.(P1, P2, P3, P4, P5, P6, P7) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(p1, p2, p3, p4, p5, p6, p7): R {
        return callMethod<R>(this@asMethod, jArgs(7) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7) })
    }
}

/**
 * Unifies usage of `call<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified P8, reified R> JMethodID.asMethod(): context(JniEnv, NativePlacement) JObject.(P1, P2, P3, P4, P5, P6, P7, P8) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(p1, p2, p3, p4, p5, p6, p7, p8): R {
        return callMethod<R>(this@asMethod, jArgs(8) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7); any(p8) })
    }
}

/**
 * Unifies usage of `call<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified P8, reified P9, reified R> JMethodID.asMethod(): context(JniEnv, NativePlacement) JObject.(P1, P2, P3, P4, P5, P6, P7, P8, P9) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(p1, p2, p3, p4, p5, p6, p7, p8, p9): R {
        return callMethod<R>(this@asMethod, jArgs(9) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7); any(p8); any(p9) })
    }
}

/**
 * Unifies usage of `call<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified P8, reified P9, reified P10, reified R> JMethodID.asMethod(): context(JniEnv, NativePlacement) JObject.(P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10): R {
        return callMethod<R>(this@asMethod, jArgs(10) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7); any(p8); any(p9); any(p10) })
    }
}

/**
 * Unifies usage of `callNonvirtual<type>Method` methods, returning lambda which internally calls method with
 * appropriate `type` and returns the result.
 *
 * Note: If the return type is a primitive type, it cannot be nullable, otherwise it must be nullable.
 */
public inline fun <reified R> JMethodID.asNonvirtualMethod(): context(JniEnv, NativePlacement) JObject.(JClass) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(cls: JClass): R {
        return callNonvirtualMethod<R>(cls, this@asNonvirtualMethod, jArgs(0) {})
    }
}

/**
 * Unifies usage of `callNonvirtual<type>Method` methods, returning lambda which internally calls method with
 * appropriate `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified R> JMethodID.asNonvirtualMethod(): context(JniEnv, NativePlacement) JObject.(JClass, P1) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(cls: JClass, p1): R {
        return callNonvirtualMethod<R>(cls, this@asNonvirtualMethod, jArgs(1) { any(p1) })
    }
}

/**
 * Unifies usage of `callNonvirtual<type>Method` methods, returning lambda which internally calls method with
 * appropriate `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified R> JMethodID.asNonvirtualMethod(): context(JniEnv, NativePlacement) JObject.(JClass, P1, P2) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(cls: JClass, p1, p2): R {
        return callNonvirtualMethod<R>(cls, this@asNonvirtualMethod, jArgs(2) { any(p1); any(p2) })
    }
}

/**
 * Unifies usage of `callNonvirtual<type>Method` methods, returning lambda which internally calls method with
 * appropriate `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified R> JMethodID.asNonvirtualMethod(): context(JniEnv, NativePlacement) JObject.(JClass, P1, P2, P3) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(cls: JClass, p1, p2, p3): R {
        return callNonvirtualMethod<R>(cls, this@asNonvirtualMethod, jArgs(3) { any(p1); any(p2); any(p3) })
    }
}

/**
 * Unifies usage of `callNonvirtual<type>Method` methods, returning lambda which internally calls method with
 * appropriate `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified R> JMethodID.asNonvirtualMethod(): context(JniEnv, NativePlacement) JObject.(JClass, P1, P2, P3, P4) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(cls: JClass, p1, p2, p3, p4): R {
        return callNonvirtualMethod<R>(cls, this@asNonvirtualMethod, jArgs(4) { any(p1); any(p2); any(p3); any(p4) })
    }
}

/**
 * Unifies usage of `callNonvirtual<type>Method` methods, returning lambda which internally calls method with
 * appropriate `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified R> JMethodID.asNonvirtualMethod(): context(JniEnv, NativePlacement) JObject.(JClass, P1, P2, P3, P4, P5) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(cls: JClass, p1, p2, p3, p4, p5): R {
        return callNonvirtualMethod<R>(cls, this@asNonvirtualMethod, jArgs(5) { any(p1); any(p2); any(p3); any(p4); any(p5) })
    }
}

/**
 * Unifies usage of `callNonvirtual<type>Method` methods, returning lambda which internally calls method with
 * appropriate `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified R> JMethodID.asNonvirtualMethod(): context(JniEnv, NativePlacement) JObject.(JClass, P1, P2, P3, P4, P5, P6) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(cls: JClass, p1, p2, p3, p4, p5, p6): R {
        return callNonvirtualMethod<R>(cls, this@asNonvirtualMethod, jArgs(6) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6) })
    }
}

/**
 * Unifies usage of `callNonvirtual<type>Method` methods, returning lambda which internally calls method with
 * appropriate `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified R> JMethodID.asNonvirtualMethod(): context(JniEnv, NativePlacement) JObject.(JClass, P1, P2, P3, P4, P5, P6, P7) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(cls: JClass, p1, p2, p3, p4, p5, p6, p7): R {
        return callNonvirtualMethod<R>(cls, this@asNonvirtualMethod, jArgs(7) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7) })
    }
}

/**
 * Unifies usage of `callNonvirtual<type>Method` methods, returning lambda which internally calls method with
 * appropriate `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified P8, reified R> JMethodID.asNonvirtualMethod(): context(JniEnv, NativePlacement) JObject.(JClass, P1, P2, P3, P4, P5, P6, P7, P8) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(cls: JClass, p1, p2, p3, p4, p5, p6, p7, p8): R {
        return callNonvirtualMethod<R>(cls, this@asNonvirtualMethod, jArgs(8) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7); any(p8) })
    }
}

/**
 * Unifies usage of `callNonvirtual<type>Method` methods, returning lambda which internally calls method with
 * appropriate `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified P8, reified P9, reified R> JMethodID.asNonvirtualMethod(): context(JniEnv, NativePlacement) JObject.(JClass, P1, P2, P3, P4, P5, P6, P7, P8, P9) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(cls: JClass, p1, p2, p3, p4, p5, p6, p7, p8, p9): R {
        return callNonvirtualMethod<R>(cls, this@asNonvirtualMethod, jArgs(9) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7); any(p8); any(p9) })
    }
}

/**
 * Unifies usage of `callNonvirtual<type>Method` methods, returning lambda which internally calls method with
 * appropriate `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified P8, reified P9, reified P10, reified R> JMethodID.asNonvirtualMethod(): context(JniEnv, NativePlacement) JObject.(JClass, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JObject.(cls: JClass, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10): R {
        return callNonvirtualMethod<R>(cls, this@asNonvirtualMethod, jArgs(10) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7); any(p8); any(p9); any(p10) })
    }
}

/**
 * Unifies usage of `callStatic<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` and returns the result.
 *
 * Note: If the return type is a primitive type, it cannot be nullable, otherwise it must be nullable.
 */
public inline fun <reified R> JMethodID.asStaticMethod(): context(JniEnv, NativePlacement) JClass.() -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(): R {
        return callStaticMethod<R>(this@asStaticMethod, jArgs(0) {})
    }
}

/**
 * Unifies usage of `callStatic<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified R> JMethodID.asStaticMethod(): context(JniEnv, NativePlacement) JClass.(P1) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1): R {
        return callStaticMethod<R>(this@asStaticMethod, jArgs(1) { any(p1) })
    }
}

/**
 * Unifies usage of `callStatic<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified R> JMethodID.asStaticMethod(): context(JniEnv, NativePlacement) JClass.(P1, P2) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2): R {
        return callStaticMethod<R>(this@asStaticMethod, jArgs(2) { any(p1); any(p2) })
    }
}

/**
 * Unifies usage of `callStatic<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified R> JMethodID.asStaticMethod(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3): R {
        return callStaticMethod<R>(this@asStaticMethod, jArgs(3) { any(p1); any(p2); any(p3) })
    }
}

/**
 * Unifies usage of `callStatic<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified R> JMethodID.asStaticMethod(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4): R {
        return callStaticMethod<R>(this@asStaticMethod, jArgs(4) { any(p1); any(p2); any(p3); any(p4) })
    }
}

/**
 * Unifies usage of `callStatic<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified R> JMethodID.asStaticMethod(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4, P5) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4, p5): R {
        return callStaticMethod<R>(this@asStaticMethod, jArgs(5) { any(p1); any(p2); any(p3); any(p4); any(p5) })
    }
}

/**
 * Unifies usage of `callStatic<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified R> JMethodID.asStaticMethod(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4, P5, P6) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4, p5, p6): R {
        return callStaticMethod<R>(this@asStaticMethod, jArgs(6) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6) })
    }
}

/**
 * Unifies usage of `callStatic<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified R> JMethodID.asStaticMethod(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4, P5, P6, P7) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4, p5, p6, p7): R {
        return callStaticMethod<R>(this@asStaticMethod, jArgs(7) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7) })
    }
}

/**
 * Unifies usage of `callStatic<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified P8, reified R> JMethodID.asStaticMethod(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4, P5, P6, P7, P8) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4, p5, p6, p7, p8): R {
        return callStaticMethod<R>(this@asStaticMethod, jArgs(8) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7); any(p8) })
    }
}

/**
 * Unifies usage of `callStatic<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified P8, reified P9, reified R> JMethodID.asStaticMethod(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4, P5, P6, P7, P8, P9) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4, p5, p6, p7, p8, p9): R {
        return callStaticMethod<R>(this@asStaticMethod, jArgs(9) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7); any(p8); any(p9) })
    }
}

/**
 * Unifies usage of `callStatic<type>Method` methods, returning lambda which internally calls method with appropriate
 * `type` setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameters and return types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified P8, reified P9, reified P10, reified R> JMethodID.asStaticMethod(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) -> R {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10): R {
        return callStaticMethod<R>(this@asStaticMethod, jArgs(10) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7); any(p8); any(p9); any(p10) })
    }
}

/**
 * Returns lambda which calls [newObject] and returns the result.
 */
public inline fun JMethodID.asConstructor(): context(JniEnv, NativePlacement) JClass.() -> JObject? {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(): JObject? {
        return newObject(this@asConstructor, jArgs(0) {})
    }
}

/**
 * Returns lambda which calls [newObject] setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameter types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1> JMethodID.asConstructor(): context(JniEnv, NativePlacement) JClass.(P1) -> JObject? {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1): JObject? {
        return newObject(this@asConstructor, jArgs(1) { any(p1) })
    }
}

/**
 * Returns lambda which calls [newObject] setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameter types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2> JMethodID.asConstructor(): context(JniEnv, NativePlacement) JClass.(P1, P2) -> JObject? {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2): JObject? {
        return newObject(this@asConstructor, jArgs(2) { any(p1); any(p2) })
    }
}

/**
 * Returns lambda which calls [newObject] setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameter types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3> JMethodID.asConstructor(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3) -> JObject? {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3): JObject? {
        return newObject(this@asConstructor, jArgs(3) { any(p1); any(p2); any(p3) })
    }
}

/**
 * Returns lambda which calls [newObject] setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameter types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4> JMethodID.asConstructor(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4) -> JObject? {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4): JObject? {
        return newObject(this@asConstructor, jArgs(4) { any(p1); any(p2); any(p3); any(p4) })
    }
}

/**
 * Returns lambda which calls [newObject] setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameter types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5> JMethodID.asConstructor(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4, P5) -> JObject? {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4, p5): JObject? {
        return newObject(this@asConstructor, jArgs(5) { any(p1); any(p2); any(p3); any(p4); any(p5) })
    }
}

/**
 * Returns lambda which calls [newObject] setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameter types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6> JMethodID.asConstructor(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4, P5, P6) -> JObject? {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4, p5, p6): JObject? {
        return newObject(this@asConstructor, jArgs(6) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6) })
    }
}

/**
 * Returns lambda which calls [newObject] setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameter types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7> JMethodID.asConstructor(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4, P5, P6, P7) -> JObject? {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4, p5, p6, p7): JObject? {
        return newObject(this@asConstructor, jArgs(7) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7) })
    }
}

/**
 * Returns lambda which calls [newObject] setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameter types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified P8> JMethodID.asConstructor(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4, P5, P6, P7, P8) -> JObject? {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4, p5, p6, p7, p8): JObject? {
        return newObject(this@asConstructor, jArgs(8) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7); any(p8) })
    }
}

/**
 * Returns lambda which calls [newObject] setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameter types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified P8, reified P9> JMethodID.asConstructor(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4, P5, P6, P7, P8, P9) -> JObject? {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4, p5, p6, p7, p8, p9): JObject? {
        return newObject(this@asConstructor, jArgs(9) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7); any(p8); any(p9) })
    }
}

/**
 * Returns lambda which calls [newObject] setting arguments with [jArgs] and returns the result.
 *
 * Note: each of the parameter types must be not-null if it's a primitive type, otherwise it must be
 * nullable.
 */
public inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6, reified P7, reified P8, reified P9, reified P10> JMethodID.asConstructor(): context(JniEnv, NativePlacement) JClass.(P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) -> JObject? {
    return context(env: JniEnv, placement: NativePlacement) fun JClass.(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10): JObject? {
        return newObject(this@asConstructor, jArgs(10) { any(p1); any(p2); any(p3); any(p4); any(p5); any(p6); any(p7); any(p8); any(p9); any(p10) })
    }
}