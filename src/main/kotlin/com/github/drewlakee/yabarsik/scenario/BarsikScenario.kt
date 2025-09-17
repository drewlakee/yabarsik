package com.github.drewlakee.yabarsik.scenario

import com.github.drewlakee.yabarsik.Barsik

interface BarsikScenario<R : BarsikScenarioResult> {
    fun play(barsik: Barsik): R
}

interface BarsikScenarioResult {
    fun isSuccessful(): Boolean
    fun message(): String
    fun sendTelegramMessage(): Boolean
}

fun <R : BarsikScenarioResult> Barsik.play(scenario: BarsikScenario<R>) = scenario.play(this)