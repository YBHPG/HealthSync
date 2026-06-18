package com.example.healthsyncandroid.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Velocity
import androidx.health.connect.client.units.Energy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

class HealthRepository(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    private val _isSupported = MutableStateFlow(false)
    val isSupported: StateFlow<Boolean> = _isSupported

    init {
        _isSupported.value = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    val requiredPermissions = setOf(
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(HeightRecord::class),
        HealthPermission.getWritePermission(RestingHeartRateRecord::class),
        HealthPermission.getWritePermission(RespiratoryRateRecord::class),
        HealthPermission.getWritePermission(Vo2MaxRecord::class),
        HealthPermission.getWritePermission(ElevationGainedRecord::class),
        HealthPermission.getWritePermission(BasalMetabolicRateRecord::class),
        HealthPermission.getWritePermission(SpeedRecord::class),
        HealthPermission.getWritePermission(StepsCadenceRecord::class)
    )

    suspend fun checkPermissions(): Boolean {
        if (!_isSupported.value) return false
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    suspend fun getGrantedPermissions(): Set<String> {
        if (!_isSupported.value) return emptySet()
        return healthConnectClient.permissionController.getGrantedPermissions()
    }

    suspend fun saveBulk(payload: BulkSyncPayload): Boolean {
        if (!_isSupported.value) return false
        return try {
            com.example.healthsyncandroid.utils.SyncLogManager.log("Started processing payload: ${payload.workouts.size} workouts, ${payload.records.size} generic records.")
            val recordsToInsert = mutableListOf<Record>()
            
            for (workout in payload.workouts) {
                val workoutMeta = Metadata.unknownRecordingMethodWithId(workout.uuid)
                var lastLapEnd = workout.startTime
                val exerciseLaps = workout.laps.sortedBy { it.startTime }.mapNotNull { lap ->
                    val lapStart = Math.max(lap.startTime, lastLapEnd)
                    val lapEnd = Math.min(lap.endTime, workout.endTime)
                    if (lapEnd > lapStart) {
                        lastLapEnd = lapEnd
                        androidx.health.connect.client.records.ExerciseLap(Instant.ofEpochMilli(lapStart), Instant.ofEpochMilli(lapEnd), null)
                    } else null
                }
                
                try {
                    recordsToInsert.add(
                        ExerciseSessionRecord(
                            startTime = Instant.ofEpochMilli(workout.startTime),
                            endTime = Instant.ofEpochMilli(workout.endTime),
                            exerciseType = mapIosActivityTypeToAndroid(workout.activityType),
                            title = "Imported Workout",
                            startZoneOffset = null,
                            endZoneOffset = null,
                            laps = exerciseLaps,
                            metadata = workoutMeta
                        )
                    )
                } catch (e: Exception) {
                    com.example.healthsyncandroid.utils.SyncLogManager.log("Failed to map Workout Session: ${e.message}")
                }
                if (workout.totalDistance > 0) {
                    try {
                        recordsToInsert.add(
                            DistanceRecord(
                                startTime = Instant.ofEpochMilli(workout.startTime),
                                endTime = Instant.ofEpochMilli(workout.endTime),
                                startZoneOffset = null,
                                endZoneOffset = null,
                                distance = Length.meters(workout.totalDistance),
                                metadata = Metadata.unknownRecordingMethodWithId(workout.uuid + "_dist")
                            )
                        )
                    } catch (e: Exception) {}
                }
                if (workout.totalEnergyBurned > 0) {
                    try {
                        recordsToInsert.add(
                            ActiveCaloriesBurnedRecord(
                                startTime = Instant.ofEpochMilli(workout.startTime),
                                endTime = Instant.ofEpochMilli(workout.endTime),
                                startZoneOffset = null,
                                endZoneOffset = null,
                                energy = Energy.kilocalories(workout.totalEnergyBurned),
                                metadata = Metadata.unknownRecordingMethodWithId(workout.uuid + "_energy")
                            )
                        )
                    } catch (e: Exception) {}
                }
                if (workout.heartRates.isNotEmpty()) {
                    val validHrSamples = workout.heartRates.filter { it.time in workout.startTime..workout.endTime && it.bpm > 0 }.map { 
                        HeartRateRecord.Sample(time = Instant.ofEpochMilli(it.time), beatsPerMinute = it.bpm.toLong()) 
                    }
                    if (validHrSamples.isNotEmpty()) {
                        try {
                            recordsToInsert.add(
                                HeartRateRecord(
                                    startTime = Instant.ofEpochMilli(workout.startTime),
                                    endTime = Instant.ofEpochMilli(workout.endTime),
                                    startZoneOffset = null,
                                    endZoneOffset = null,
                                    samples = validHrSamples,
                                    metadata = Metadata.unknownRecordingMethodWithId(workout.uuid + "_hr")
                                )
                            )
                        } catch (e: Exception) {}
                    }
                }
                if (workout.cadences.isNotEmpty()) {
                    val validCadenceSamples = workout.cadences.filter { it.time in workout.startTime..workout.endTime && it.rpm > 0 }.map {
                        StepsCadenceRecord.Sample(time = Instant.ofEpochMilli(it.time), rate = it.rpm)
                    }
                    if (validCadenceSamples.isNotEmpty()) {
                        try {
                            recordsToInsert.add(
                                StepsCadenceRecord(
                                    startTime = Instant.ofEpochMilli(workout.startTime),
                                    endTime = Instant.ofEpochMilli(workout.endTime),
                                    startZoneOffset = null,
                                    endZoneOffset = null,
                                    samples = validCadenceSamples,
                                    metadata = Metadata.unknownRecordingMethodWithId(workout.uuid + "_cadence")
                                )
                            )
                        } catch (e: Exception) {}
                    }
                }
                if (workout.speeds.isNotEmpty()) {
                    val validSpeedSamples = workout.speeds.filter { it.time in workout.startTime..workout.endTime && it.speed >= 0 }.map {
                        SpeedRecord.Sample(time = Instant.ofEpochMilli(it.time), speed = Velocity.metersPerSecond(it.speed))
                    }
                    if (validSpeedSamples.isNotEmpty()) {
                        try {
                            recordsToInsert.add(
                                SpeedRecord(
                                    startTime = Instant.ofEpochMilli(workout.startTime),
                                    endTime = Instant.ofEpochMilli(workout.endTime),
                                    startZoneOffset = null,
                                    endZoneOffset = null,
                                    samples = validSpeedSamples,
                                    metadata = Metadata.unknownRecordingMethodWithId(workout.uuid + "_speed")
                                )
                            )
                        } catch (e: Exception) {}
                    }
                }
            }
            
            val sleepRecordsDto = payload.records.filter { it.type == "SleepAnalysis" }.sortedBy { it.startTime }
            if (sleepRecordsDto.isNotEmpty()) {
                val sessions = mutableListOf<MutableList<com.example.healthsyncandroid.data.GenericRecordDto>>()
                var currentSession = mutableListOf<com.example.healthsyncandroid.data.GenericRecordDto>()
                for (rec in sleepRecordsDto) {
                    if (currentSession.isEmpty()) {
                        currentSession.add(rec)
                    } else {
                        val lastRec = currentSession.last()
                        if (rec.startTime - lastRec.endTime < 2 * 60 * 60 * 1000) {
                            currentSession.add(rec)
                        } else {
                            sessions.add(currentSession)
                            currentSession = mutableListOf(rec)
                        }
                    }
                }
                if (currentSession.isNotEmpty()) sessions.add(currentSession)
                
                for (session in sessions) {
                    val minStart = session.minOf { it.startTime }
                    val maxEnd = session.maxOf { it.endTime }
                    val start = Instant.ofEpochMilli(minStart)
                    val end = Instant.ofEpochMilli(Math.max(maxEnd, minStart + 1000))
                    var totalSleepMs = 0L
                    var deepSleepMs = 0L
                    var remSleepMs = 0L
                    
                    var lastStageEnd = minStart
                    val stages = session.sortedBy { it.startTime }.mapNotNull {
                        val stStart = Math.max(it.startTime, lastStageEnd)
                        val stEnd = Math.min(it.endTime, maxEnd)
                        val duration = stEnd - stStart
                        val stageType = when (it.value.toInt()) {
                            0 -> null // inBed doesn't have a direct stage mapping, we skip adding it to stages
                            1 -> { totalSleepMs += duration; SleepSessionRecord.STAGE_TYPE_SLEEPING }
                            2 -> SleepSessionRecord.STAGE_TYPE_AWAKE
                            3 -> { totalSleepMs += duration; SleepSessionRecord.STAGE_TYPE_LIGHT }
                            4 -> { totalSleepMs += duration; deepSleepMs += duration; SleepSessionRecord.STAGE_TYPE_DEEP }
                            5 -> { totalSleepMs += duration; remSleepMs += duration; SleepSessionRecord.STAGE_TYPE_REM }
                            else -> SleepSessionRecord.STAGE_TYPE_UNKNOWN
                        }
                        if (stageType != null && stEnd > stStart) {
                            lastStageEnd = stEnd
                            SleepSessionRecord.Stage(Instant.ofEpochMilli(stStart), Instant.ofEpochMilli(stEnd), stageType)
                        } else null
                    }
                    
                    var score = 0.0
                    val totalSleepHours = totalSleepMs / (1000.0 * 60 * 60)
                    score += Math.min(50.0, (totalSleepHours / 8.0) * 50.0)
                    if (totalSleepMs > 0) {
                        val deepPercent = deepSleepMs.toDouble() / totalSleepMs
                        score += Math.min(25.0, (deepPercent / 0.15) * 25.0)
                        
                        val remPercent = remSleepMs.toDouble() / totalSleepMs
                        score += Math.min(25.0, (remPercent / 0.20) * 25.0)
                    }
                    val finalScore = score.toInt()

                    try {
                        recordsToInsert.add(
                            SleepSessionRecord(
                                startTime = start,
                                endTime = end,
                                startZoneOffset = null,
                                endZoneOffset = null,
                                title = "Sleep Score: $finalScore",
                                notes = "Calculated Sleep Score: $finalScore/100",
                                stages = stages,
                                metadata = Metadata.unknownRecordingMethodWithId(session.first().uuid + "_session")
                            )
                        )
                    } catch (e: Exception) {
                        com.example.healthsyncandroid.utils.SyncLogManager.log("Failed to map Sleep Session: ${e.message}")
                    }
                }
            }

            for (rec in payload.records) {
                val recMeta = Metadata.unknownRecordingMethodWithId(rec.uuid)
                val start = Instant.ofEpochMilli(rec.startTime)
                val end = if (rec.endTime > rec.startTime) Instant.ofEpochMilli(rec.endTime) else Instant.ofEpochMilli(rec.startTime + 1000)
                
                try {
                    when (rec.type) {
                        "HeartRate" -> if (rec.value > 0) recordsToInsert.add(
                            HeartRateRecord(
                                startTime = start,
                                endTime = end,
                                startZoneOffset = null,
                                endZoneOffset = null,
                                samples = listOf(HeartRateRecord.Sample(time = start, beatsPerMinute = rec.value.toLong())),
                                metadata = recMeta
                            )
                        )
                        "StepCount" -> if (rec.value > 0) recordsToInsert.add(
                            StepsRecord(count = rec.value.toLong(), startTime = start, endTime = end, startZoneOffset = null, endZoneOffset = null, metadata = recMeta)
                        )
                        "ActiveEnergyBurned" -> if (rec.value > 0) recordsToInsert.add(
                            ActiveCaloriesBurnedRecord(energy = Energy.kilocalories(rec.value), startTime = start, endTime = end, startZoneOffset = null, endZoneOffset = null, metadata = recMeta)
                        )
                        "DistanceWalkingRunning", "DistanceCycling", "DistanceSwimming" -> if (rec.value > 0) recordsToInsert.add(
                            DistanceRecord(distance = Length.meters(rec.value), startTime = start, endTime = end, startZoneOffset = null, endZoneOffset = null, metadata = recMeta)
                        )
                        "BodyMass" -> {
                            if (rec.value > 0) {
                                try {
                                    recordsToInsert.add(
                                        WeightRecord(weight = Mass.kilograms(rec.value), time = start, zoneOffset = null, metadata = recMeta)
                                    )
                                    com.example.healthsyncandroid.utils.SyncLogManager.log("DEBUG BodyMass: mapped ${rec.value} kg at $start")
                                } catch (e: Exception) {
                                    com.example.healthsyncandroid.utils.SyncLogManager.log("DEBUG BodyMass Error: ${e.message} for ${rec.value} kg")
                                }
                            } else {
                                com.example.healthsyncandroid.utils.SyncLogManager.log("DEBUG BodyMass: Ignored negative or zero value ${rec.value}")
                            }
                        }
                        "Height" -> if (rec.value > 0) recordsToInsert.add(
                            HeightRecord(height = Length.meters(rec.value), time = start, zoneOffset = null, metadata = recMeta)
                        )
                        "RestingHeartRate" -> if (rec.value > 0) recordsToInsert.add(
                            RestingHeartRateRecord(beatsPerMinute = rec.value.toLong(), time = start, zoneOffset = null, metadata = recMeta)
                        )
                        "RespiratoryRate" -> if (rec.value > 0) recordsToInsert.add(
                            RespiratoryRateRecord(rate = rec.value, time = start, zoneOffset = null, metadata = recMeta)
                        )
                        "VO2Max" -> if (rec.value > 0) recordsToInsert.add(
                            Vo2MaxRecord(vo2MillilitersPerMinuteKilogram = rec.value, measurementMethod = Vo2MaxRecord.MEASUREMENT_METHOD_OTHER, time = start, zoneOffset = null, metadata = recMeta)
                        )
                        "FlightsClimbed" -> if (rec.value > 0) recordsToInsert.add(
                            ElevationGainedRecord(elevation = Length.meters(rec.value * 3.0), startTime = start, endTime = end, startZoneOffset = null, endZoneOffset = null, metadata = recMeta)
                        )
                        "BasalEnergyBurned" -> {
                            val durationSeconds = (rec.endTime - rec.startTime) / 1000.0
                            if (durationSeconds > 0 && rec.value > 0) {
                                val kcalPerDay = (rec.value / durationSeconds) * 86400.0
                                recordsToInsert.add(
                                    BasalMetabolicRateRecord(basalMetabolicRate = Power.kilocaloriesPerDay(kcalPerDay), time = start, zoneOffset = null, metadata = recMeta)
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip gracefully mapping error for generic record
                }
            }
            
            if (recordsToInsert.isNotEmpty()) {
                com.example.healthsyncandroid.utils.SyncLogManager.log("Inserting ${recordsToInsert.size} records into Health Connect in chunks...")
                var successCount = 0
                var failCount = 0

                recordsToInsert.chunked(3000).forEachIndexed { index, chunk ->
                    try {
                        healthConnectClient.insertRecords(chunk)
                        successCount += chunk.size
                    } catch (e: Exception) {
                        com.example.healthsyncandroid.utils.SyncLogManager.log("Chunk $index failed: ${e.message}. Attempting individual inserts...")
                        for (record in chunk) {
                            try {
                                healthConnectClient.insertRecords(listOf(record))
                                successCount++
                            } catch (innerE: Exception) {
                                failCount++
                                val recName = record::class.java.simpleName
                                if (failCount <= 20) { // Limit error spam
                                    com.example.healthsyncandroid.utils.SyncLogManager.log("Failed record: $recName. Error: ${innerE.message}")
                                }
                            }
                        }
                    }
                }
                com.example.healthsyncandroid.utils.SyncLogManager.log("Insertion complete. Success: $successCount, Failed: $failCount.")
            } else {
                com.example.healthsyncandroid.utils.SyncLogManager.log("No records to insert.")
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            com.example.healthsyncandroid.utils.SyncLogManager.log("Fatal Error during bulk sync: ${e.message}")
            false
        }
    }

    private fun mapIosActivityTypeToAndroid(iosType: Int): Int {
        return when (iosType) {
            37 -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING // HKWorkoutActivityTypeRunning
            52 -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING // HKWorkoutActivityTypeWalking
            13 -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING // HKWorkoutActivityTypeCycling
            20 -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING // HKWorkoutActivityTypeFunctionalStrengthTraining
            50 -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING // HKWorkoutActivityTypeTraditionalStrengthTraining
            46 -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL // HKWorkoutActivityTypeSwimming
            16 -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL // HKWorkoutActivityTypeElliptical
            else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        }
    }
}
