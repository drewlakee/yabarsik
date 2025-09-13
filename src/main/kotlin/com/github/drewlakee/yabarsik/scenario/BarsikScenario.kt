package com.github.drewlakee.yabarsik.scenario

import com.github.drewlakee.yabarsik.Barsik
import dev.forkhandles.result4k.Result4k

interface BarsikScenario<R> {
    fun play(barsik: Barsik): Result4k<R, Throwable>
}

fun <R> Barsik.play(scenario: BarsikScenario<R>) = scenario.play(this)