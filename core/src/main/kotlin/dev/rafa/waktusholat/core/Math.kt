package dev.rafa.waktusholat.core

/** Floor division and modulo for negative operands, matching Python's `//` and `%`. */
internal fun floorDiv(a: Int, b: Int): Int {
    var q = a / b
    if (a % b != 0 && (a xor b) < 0) q--
    return q
}

internal fun floorMod(a: Int, b: Int): Int = a - floorDiv(a, b) * b
