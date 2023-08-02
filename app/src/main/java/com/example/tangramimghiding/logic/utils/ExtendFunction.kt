package com.example.tangramimghiding.logic.utils


fun BooleanArray.fillWith(value: Boolean) {
    for (i in 0 until  this.size) {
        this[i] = value
    }
}

fun DoubleArray.fillWith(value: Double) {
    for (i in 0 until this.size) {
        this[i] = value
    }
}

fun IntArray.fillWith(init: (Int)->Int) {
    for (i in 0 until this.size) {
        this[i] = init(i)
    }
}