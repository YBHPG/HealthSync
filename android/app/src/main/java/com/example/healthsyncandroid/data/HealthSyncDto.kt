package com.example.healthsyncandroid.data

import kotlinx.serialization.Serializable

@Serializable
data class BulkSyncPayload(
    val records: List<GenericRecordDto>,
    val workouts: List<WorkoutDto>
)

@Serializable
data class GenericRecordDto(
    val uuid: String,
    val type: String,
    val startTime: Long,
    val endTime: Long,
    val value: Double
)

@Serializable
data class WorkoutDto(
    val uuid: String,
    val activityType: Int,
    val startTime: Long,
    val endTime: Long,
    val totalEnergyBurned: Double,
    val totalDistance: Double,
    val heartRates: List<HeartRateDto>,
    val distances: List<DistanceDto>,
    val laps: List<LapDto> = emptyList(),
    val cadences: List<CadenceDto> = emptyList(),
    val speeds: List<SpeedDto> = emptyList()
)

@Serializable
data class DistanceDto(
    val time: Long,
    val meters: Double
)

@Serializable
data class StepsDto(
    val startTime: Long,
    val endTime: Long,
    val count: Long
)

@Serializable
data class HeartRateDto(
    val time: Long,
    val bpm: Double
)

@Serializable
data class WeightDto(
    val time: Long,
    val kg: Double
)

@Serializable
data class LapDto(
    val startTime: Long,
    val endTime: Long
)

@Serializable
data class CadenceDto(
    val time: Long,
    val rpm: Double
)

@Serializable
data class SpeedDto(
    val time: Long,
    val speed: Double
)
