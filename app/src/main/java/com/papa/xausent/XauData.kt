package com.papa.xausent

data class XauData(
    val buyPct: Int,
    val sellPct: Int,
    val buyEntries: Int,
    val sellEntries: Int,
    val price: Double?,
    val t1: Double?,
    val t2: Double?,
    val flow: String,
    val longPct: Int?,
    val shortPct: Int?,
    val sample: String,
    val updatedEpochMs: Long
)
