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
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Velocity
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Volume
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
        HealthPermission.getWritePermission(StepsCadenceRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(LeanBodyMassRecord::class),
        HealthPermission.getWritePermission(BoneMassRecord::class),
        HealthPermission.getWritePermission(BodyTemperatureRecord::class),
        HealthPermission.getWritePermission(BasalBodyTemperatureRecord::class),
        HealthPermission.getWritePermission(BloodGlucoseRecord::class),
        HealthPermission.getWritePermission(OxygenSaturationRecord::class),
        HealthPermission.getWritePermission(BloodPressureRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getWritePermission(WheelchairPushesRecord::class),
        HealthPermission.getWritePermission(MenstruationFlowRecord::class),
        HealthPermission.getWritePermission(IntermenstrualBleedingRecord::class),
        HealthPermission.getWritePermission(CervicalMucusRecord::class),
        HealthPermission.getWritePermission(OvulationTestRecord::class),
        HealthPermission.getWritePermission(SexualActivityRecord::class)
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
            val workoutCounts = payload.workouts.groupingBy { it.activityType }.eachCount()
            val recordCountsRaw = payload.records.groupingBy { it.type }.eachCount()
            com.example.healthsyncandroid.utils.SyncLogManager.log("Started processing payload: ${payload.workouts.size} workouts (Types: $workoutCounts), ${payload.records.size} generic records (Types: $recordCountsRaw).")
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

            val bpRecords = payload.records.filter { it.type == "BloodPressureSystolic" || it.type == "BloodPressureDiastolic" }
            bpRecords.groupBy { it.startTime }.forEach { (time, records) ->
                val sys = records.find { it.type == "BloodPressureSystolic" }?.value
                val dia = records.find { it.type == "BloodPressureDiastolic" }?.value
                if (sys != null && dia != null) {
                    try {
                        recordsToInsert.add(
                            BloodPressureRecord(
                                time = Instant.ofEpochMilli(time),
                                zoneOffset = null,
                                systolic = Pressure.millimetersOfMercury(sys),
                                diastolic = Pressure.millimetersOfMercury(dia),
                                metadata = Metadata.unknownRecordingMethodWithId(records.first().uuid)
                            )
                        )
                    } catch (e: Exception) {}
                }
            }

            val nutritionRecords = payload.records.filter { it.type == "DietaryCarbs" || it.type == "DietaryProtein" || it.type == "DietaryFat" || it.type == "DietaryEnergy" }
            nutritionRecords.groupBy { it.startTime }.forEach { (time, records) ->
                val carbs = records.find { it.type == "DietaryCarbs" }?.value
                val protein = records.find { it.type == "DietaryProtein" }?.value
                val fat = records.find { it.type == "DietaryFat" }?.value
                val energy = records.find { it.type == "DietaryEnergy" }?.value
                if (carbs != null || protein != null || fat != null || energy != null) {
                    val start = Instant.ofEpochMilli(time)
                    val end = Instant.ofEpochMilli(records.first().endTime.coerceAtLeast(time + 1000))
                    try {
                        recordsToInsert.add(
                            NutritionRecord(
                                startTime = start,
                                endTime = end,
                                startZoneOffset = null,
                                endZoneOffset = null,
                                totalCarbohydrate = carbs?.let { Mass.grams(it) },
                                protein = protein?.let { Mass.grams(it) },
                                totalFat = fat?.let { Mass.grams(it) },
                                energy = energy?.let { Energy.kilocalories(it) },
                                metadata = Metadata.unknownRecordingMethodWithId(records.first().uuid)
                            )
                        )
                    } catch (e: Exception) {}
                }
            }

            val handledGroupTypes = setOf("BloodPressureSystolic", "BloodPressureDiastolic", "DietaryCarbs", "DietaryProtein", "DietaryFat", "DietaryEnergy", "SleepAnalysis")

            for (rec in payload.records) {
                if (handledGroupTypes.contains(rec.type)) continue
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
                        "DistanceWalkingRunning", "DistanceCycling", "DistanceSwimming", "DistanceWheelchair" -> if (rec.value > 0) recordsToInsert.add(
                            DistanceRecord(distance = Length.meters(rec.value), startTime = start, endTime = end, startZoneOffset = null, endZoneOffset = null, metadata = recMeta)
                        )
                        "PushCount" -> if (rec.value > 0) recordsToInsert.add(
                            WheelchairPushesRecord(count = rec.value.toLong(), startTime = start, endTime = end, startZoneOffset = null, endZoneOffset = null, metadata = recMeta)
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
                        "BodyFatPercentage" -> if (rec.value > 0) recordsToInsert.add(
                            BodyFatRecord(percentage = Percentage(rec.value), time = start, zoneOffset = null, metadata = recMeta)
                        )
                        "LeanBodyMass" -> if (rec.value > 0) recordsToInsert.add(
                            LeanBodyMassRecord(mass = Mass.kilograms(rec.value), time = start, zoneOffset = null, metadata = recMeta)
                        )
                        "BodyTemperature" -> if (rec.value > 0) recordsToInsert.add(
                            BodyTemperatureRecord(temperature = Temperature.celsius(rec.value), time = start, zoneOffset = null, metadata = recMeta)
                        )
                        "BasalBodyTemperature" -> if (rec.value > 0) recordsToInsert.add(
                            BasalBodyTemperatureRecord(temperature = Temperature.celsius(rec.value), time = start, zoneOffset = null, metadata = recMeta)
                        )
                        "BloodGlucose" -> if (rec.value > 0) recordsToInsert.add(
                            BloodGlucoseRecord(level = BloodGlucose.milligramsPerDeciliter(rec.value), time = start, zoneOffset = null, metadata = recMeta)
                        )
                        "OxygenSaturation" -> if (rec.value > 0) recordsToInsert.add(
                            OxygenSaturationRecord(percentage = Percentage(rec.value), time = start, zoneOffset = null, metadata = recMeta)
                        )
                        "DietaryWater" -> if (rec.value > 0) recordsToInsert.add(
                            HydrationRecord(volume = Volume.liters(rec.value), startTime = start, endTime = end, startZoneOffset = null, endZoneOffset = null, metadata = recMeta)
                        )
                        "MenstruationFlow" -> {
                            val flow = when (rec.value.toInt()) {
                                2 -> MenstruationFlowRecord.FLOW_LIGHT
                                3 -> MenstruationFlowRecord.FLOW_MEDIUM
                                4 -> MenstruationFlowRecord.FLOW_HEAVY
                                else -> null
                            }
                            if (flow != null) {
                                recordsToInsert.add(MenstruationFlowRecord(time = start, zoneOffset = null, flow = flow, metadata = recMeta))
                            }
                        }
                        "IntermenstrualBleeding" -> recordsToInsert.add(
                            IntermenstrualBleedingRecord(time = start, zoneOffset = null, metadata = recMeta)
                        )
                        "CervicalMucusQuality" -> {
                            val appearance = when (rec.value.toInt()) {
                                1 -> CervicalMucusRecord.APPEARANCE_DRY
                                2 -> CervicalMucusRecord.APPEARANCE_STICKY
                                3 -> CervicalMucusRecord.APPEARANCE_CREAMY
                                4 -> CervicalMucusRecord.APPEARANCE_WATERY
                                5 -> CervicalMucusRecord.APPEARANCE_EGG_WHITE
                                else -> null
                            }
                            if (appearance != null) {
                                recordsToInsert.add(CervicalMucusRecord(time = start, zoneOffset = null, appearance = appearance, metadata = recMeta))
                            }
                        }
                        "OvulationTestResult" -> {
                            val result = when (rec.value.toInt()) {
                                1 -> OvulationTestRecord.RESULT_NEGATIVE
                                2 -> OvulationTestRecord.RESULT_POSITIVE
                                4 -> OvulationTestRecord.RESULT_HIGH // Estrogen surge
                                else -> null
                            }
                            if (result != null) {
                                recordsToInsert.add(OvulationTestRecord(time = start, zoneOffset = null, result = result, metadata = recMeta))
                            }
                        }
                        "SexualActivity" -> {
                            recordsToInsert.add(SexualActivityRecord(time = start, zoneOffset = null, metadata = recMeta))
                        }
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
                val recordCounts = recordsToInsert.groupingBy { it::class.java.simpleName }.eachCount()
                val countSummary = recordCounts.entries.joinToString { "${it.key}: ${it.value}" }
                com.example.healthsyncandroid.utils.SyncLogManager.log("Inserting ${recordsToInsert.size} records into Health Connect...\nBreakdown: $countSummary")
                var successCount = 0
                var failCount = 0

                val totalRecords = recordsToInsert.size
                val chunkSize = 3000
                val logThresholdStep = Math.max(1, totalRecords / 10)
                var nextLogThreshold = logThresholdStep
                var processedCount = 0

                recordsToInsert.chunked(chunkSize).forEachIndexed { index, chunk ->
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
                    processedCount += chunk.size
                    if (processedCount >= nextLogThreshold || processedCount == totalRecords) {
                        val percentage = (processedCount * 100) / totalRecords
                        com.example.healthsyncandroid.utils.SyncLogManager.log("Progress: $processedCount / $totalRecords ($percentage%)")
                        nextLogThreshold += logThresholdStep
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
