import HealthKit
import os.log
import Foundation

final class HealthKitManager: ObservableObject, @unchecked Sendable {
    static let shared = HealthKitManager()
    let healthStore = HKHealthStore()
    private let logger = OSLog(subsystem: "com.bulbadyshka.HealthSync", category: "HealthKit")
    
    @Published var syncLogs: [String] = []
    @Published var isSyncing: Bool = false
    
    private init() {}
    
    func log(_ message: String) {
        DispatchQueue.main.async {
            self.syncLogs.append(message)
            if self.syncLogs.count > 50 {
                self.syncLogs.removeFirst()
            }
        }
    }
    
    func requestAuthorization(completion: @escaping @Sendable (Bool) -> Void) {
        guard HKHealthStore.isHealthDataAvailable() else {
            completion(false)
            return
        }
        
        let typesToRead: Set<HKObjectType> = [
            HKQuantityType.quantityType(forIdentifier: .stepCount)!,
            HKQuantityType.quantityType(forIdentifier: .heartRate)!,
            HKQuantityType.quantityType(forIdentifier: .bodyMass)!,
            HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned)!,
            HKQuantityType.quantityType(forIdentifier: .distanceWalkingRunning)!,
            HKQuantityType.quantityType(forIdentifier: .height)!,
            HKQuantityType.quantityType(forIdentifier: .restingHeartRate)!,
            HKQuantityType.quantityType(forIdentifier: .respiratoryRate)!,
            HKQuantityType.quantityType(forIdentifier: .vo2Max)!,
            HKQuantityType.quantityType(forIdentifier: .flightsClimbed)!,
            HKQuantityType.quantityType(forIdentifier: .distanceCycling)!,
            HKQuantityType.quantityType(forIdentifier: .distanceSwimming)!,
            HKQuantityType.quantityType(forIdentifier: .basalEnergyBurned)!,
            HKQuantityType.quantityType(forIdentifier: .runningSpeed)!,
            HKQuantityType.quantityType(forIdentifier: .bodyFatPercentage)!,
            HKQuantityType.quantityType(forIdentifier: .leanBodyMass)!,
            HKQuantityType.quantityType(forIdentifier: .bodyTemperature)!,
            HKQuantityType.quantityType(forIdentifier: .basalBodyTemperature)!,
            HKQuantityType.quantityType(forIdentifier: .bloodGlucose)!,
            HKQuantityType.quantityType(forIdentifier: .oxygenSaturation)!,
            HKQuantityType.quantityType(forIdentifier: .bloodPressureSystolic)!,
            HKQuantityType.quantityType(forIdentifier: .bloodPressureDiastolic)!,
            HKQuantityType.quantityType(forIdentifier: .dietaryWater)!,
            HKQuantityType.quantityType(forIdentifier: .dietaryCarbohydrates)!,
            HKQuantityType.quantityType(forIdentifier: .dietaryProtein)!,
            HKQuantityType.quantityType(forIdentifier: .dietaryFatTotal)!,
            HKQuantityType.quantityType(forIdentifier: .dietaryEnergyConsumed)!,
            HKQuantityType.quantityType(forIdentifier: .pushCount)!,
            HKQuantityType.quantityType(forIdentifier: .distanceWheelchair)!,
            HKCategoryType.categoryType(forIdentifier: .sleepAnalysis)!,
            HKCategoryType.categoryType(forIdentifier: .menstrualFlow)!,
            HKCategoryType.categoryType(forIdentifier: .intermenstrualBleeding)!,
            HKCategoryType.categoryType(forIdentifier: .cervicalMucusQuality)!,
            HKCategoryType.categoryType(forIdentifier: .ovulationTestResult)!,
            HKCategoryType.categoryType(forIdentifier: .sexualActivity)!,
            HKObjectType.workoutType()
        ]
        
        healthStore.requestAuthorization(toShare: nil, read: typesToRead) { [weak self] success, error in
            if let error = error, let logger = self?.logger {
                os_log("HealthKit auth error: %{public}@", log: logger, type: .error, error.localizedDescription)
            }
            completion(success)
        }
    }
    
    func fetchAllData(forLastDays days: Int, completion: @escaping @Sendable (BulkSyncPayload?) -> Void) {
        let now = Date()
        guard let startDate = Calendar.current.date(byAdding: .day, value: -days, to: now) else {
            completion(nil)
            return
        }
        
        log("Starting fetch for last \(days) days...")
        DispatchQueue.main.async { self.isSyncing = true }
        
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: now, options: .strictStartDate)
        
        let workoutQuery = HKSampleQuery(sampleType: HKObjectType.workoutType(), predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { [weak self] query, samples, error in
            guard let workouts = samples as? [HKWorkout], error == nil, let self = self else {
                self?.log("Failed to fetch workouts: \(error?.localizedDescription ?? "Unknown error")")
                DispatchQueue.main.async { self?.isSyncing = false }
                completion(nil)
                return
            }
            
            self.log("Found \(workouts.count) workouts. Processing...")
            
            final class ProtectedArray<T>: @unchecked Sendable {
                private var array: [T] = []
                private let lock = NSLock()
                func append(_ element: T) {
                    lock.lock()
                    array.append(element)
                    lock.unlock()
                }
                func get() -> [T] {
                    lock.lock()
                    defer { lock.unlock() }
                    return array
                }
            }
            
            let workoutDtos = ProtectedArray<WorkoutDto>()
            let group = DispatchGroup()
            
            for workout in workouts {
                group.enter()
                self.fetchSamplesForWorkout(workout: workout) { hrSamples, distanceSamples, cadSamples, speedSamples, lapSamples in
                    let activeEnergyType = HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned)!
                    let distanceType = HKQuantityType.quantityType(forIdentifier: .distanceWalkingRunning)!
                    
                    let energy = workout.statistics(for: activeEnergyType)?.sumQuantity()?.doubleValue(for: .kilocalorie()) ?? 0
                    let distance = workout.statistics(for: distanceType)?.sumQuantity()?.doubleValue(for: .meter()) ?? 0
                    
                    let dto = WorkoutDto(
                        uuid: workout.uuid.uuidString,
                        activityType: Int(workout.workoutActivityType.rawValue),
                        startTime: Int64(workout.startDate.timeIntervalSince1970 * 1000),
                        endTime: Int64(workout.endDate.timeIntervalSince1970 * 1000),
                        totalEnergyBurned: energy,
                        totalDistance: distance,
                        heartRates: hrSamples,
                        distances: distanceSamples,
                        laps: lapSamples,
                        cadences: cadSamples,
                        speeds: speedSamples
                    )
                    workoutDtos.append(dto)
                    group.leave()
                }
            }
            
            group.notify(queue: .main) {
                self.log("Workouts processed. Fetching background metrics...")
                self.fetchBackgroundMetrics(startDate: startDate, endDate: now) { backgroundRecords in
                    self.fetchCategoryMetrics(startDate: startDate, endDate: now) { categoryRecords in
                        var allRecords = backgroundRecords
                        allRecords.append(contentsOf: categoryRecords)
                        
                        let payload = BulkSyncPayload(records: allRecords, workouts: workoutDtos.get())
                        self.log("Finished processing. Total payload contains \(payload.workouts.count) workouts and \(payload.records.count) background records.")
                        completion(payload)
                    }
                }
            }
        }
        
        healthStore.execute(workoutQuery)
    }
    
    private func fetchCategoryMetrics(startDate: Date, endDate: Date, completion: @escaping @Sendable ([GenericRecordDto]) -> Void) {
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: .strictStartDate)
        
        let typesToFetch = [
            ("SleepAnalysis", HKCategoryType.categoryType(forIdentifier: .sleepAnalysis)!),
            ("MenstrualFlow", HKCategoryType.categoryType(forIdentifier: .menstrualFlow)!),
            ("IntermenstrualBleeding", HKCategoryType.categoryType(forIdentifier: .intermenstrualBleeding)!),
            ("CervicalMucusQuality", HKCategoryType.categoryType(forIdentifier: .cervicalMucusQuality)!),
            ("OvulationTestResult", HKCategoryType.categoryType(forIdentifier: .ovulationTestResult)!),
            ("SexualActivity", HKCategoryType.categoryType(forIdentifier: .sexualActivity)!)
        ]
        
        final class ProtectedArray<T>: @unchecked Sendable {
            private var array: [T] = []
            private let lock = NSLock()
            func append(contentsOf elements: [T]) {
                lock.lock()
                array.append(contentsOf: elements)
                lock.unlock()
            }
            func get() -> [T] {
                lock.lock()
                defer { lock.unlock() }
                return array
            }
        }
        
        let categoryRecords = ProtectedArray<GenericRecordDto>()
        let group = DispatchGroup()
        
        for (typeName, hkType) in typesToFetch {
            group.enter()
            let query = HKSampleQuery(sampleType: hkType, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { [weak self] _, samples, _ in
                if let categorySamples = samples as? [HKCategorySample], !categorySamples.isEmpty {
                    self?.log("Fetched \(categorySamples.count) samples for \(typeName).")
                    var records = [GenericRecordDto]()
                    for sample in categorySamples {
                        records.append(GenericRecordDto(
                            uuid: sample.uuid.uuidString,
                            type: typeName,
                            startTime: Int64(sample.startDate.timeIntervalSince1970 * 1000),
                            endTime: Int64(sample.endDate.timeIntervalSince1970 * 1000),
                            value: Double(sample.value)
                        ))
                    }
                    categoryRecords.append(contentsOf: records)
                }
                group.leave()
            }
            healthStore.execute(query)
        }
        
        group.notify(queue: .main) {
            completion(categoryRecords.get())
        }
    }
    
    private func fetchBackgroundMetrics(startDate: Date, endDate: Date, completion: @escaping @Sendable ([GenericRecordDto]) -> Void) {
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: .strictStartDate)
        
        let typesToFetch = [
            ("HeartRate", HKQuantityType.quantityType(forIdentifier: .heartRate)!),
            ("StepCount", HKQuantityType.quantityType(forIdentifier: .stepCount)!),
            ("ActiveEnergyBurned", HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned)!),
            ("DistanceWalkingRunning", HKQuantityType.quantityType(forIdentifier: .distanceWalkingRunning)!),
            ("BodyMass", HKQuantityType.quantityType(forIdentifier: .bodyMass)!),
            ("Height", HKQuantityType.quantityType(forIdentifier: .height)!),
            ("RestingHeartRate", HKQuantityType.quantityType(forIdentifier: .restingHeartRate)!),
            ("RespiratoryRate", HKQuantityType.quantityType(forIdentifier: .respiratoryRate)!),
            ("VO2Max", HKQuantityType.quantityType(forIdentifier: .vo2Max)!),
            ("FlightsClimbed", HKQuantityType.quantityType(forIdentifier: .flightsClimbed)!),
            ("DistanceCycling", HKQuantityType.quantityType(forIdentifier: .distanceCycling)!),
            ("DistanceSwimming", HKQuantityType.quantityType(forIdentifier: .distanceSwimming)!),
            ("BasalEnergyBurned", HKQuantityType.quantityType(forIdentifier: .basalEnergyBurned)!),
            ("BodyFatPercentage", HKQuantityType.quantityType(forIdentifier: .bodyFatPercentage)!),
            ("LeanBodyMass", HKQuantityType.quantityType(forIdentifier: .leanBodyMass)!),
            ("BodyTemperature", HKQuantityType.quantityType(forIdentifier: .bodyTemperature)!),
            ("BasalBodyTemperature", HKQuantityType.quantityType(forIdentifier: .basalBodyTemperature)!),
            ("BloodGlucose", HKQuantityType.quantityType(forIdentifier: .bloodGlucose)!),
            ("OxygenSaturation", HKQuantityType.quantityType(forIdentifier: .oxygenSaturation)!),
            ("BloodPressureSystolic", HKQuantityType.quantityType(forIdentifier: .bloodPressureSystolic)!),
            ("BloodPressureDiastolic", HKQuantityType.quantityType(forIdentifier: .bloodPressureDiastolic)!),
            ("DietaryWater", HKQuantityType.quantityType(forIdentifier: .dietaryWater)!),
            ("DietaryCarbs", HKQuantityType.quantityType(forIdentifier: .dietaryCarbohydrates)!),
            ("DietaryProtein", HKQuantityType.quantityType(forIdentifier: .dietaryProtein)!),
            ("DietaryFat", HKQuantityType.quantityType(forIdentifier: .dietaryFatTotal)!),
            ("DietaryEnergy", HKQuantityType.quantityType(forIdentifier: .dietaryEnergyConsumed)!),
            ("PushCount", HKQuantityType.quantityType(forIdentifier: .pushCount)!),
            ("DistanceWheelchair", HKQuantityType.quantityType(forIdentifier: .distanceWheelchair)!)
        ]
        
        final class ProtectedArray<T>: @unchecked Sendable {
            private var array: [T] = []
            private let lock = NSLock()
            func append(contentsOf elements: [T]) {
                lock.lock()
                array.append(contentsOf: elements)
                lock.unlock()
            }
            func get() -> [T] {
                lock.lock()
                defer { lock.unlock() }
                return array
            }
        }
        
        let backgroundRecords = ProtectedArray<GenericRecordDto>()
        let group = DispatchGroup()
        
        for (typeName, hkType) in typesToFetch {
            group.enter()
            let query = HKSampleQuery(sampleType: hkType, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { [weak self] _, samples, _ in
                if let quantitySamples = samples as? [HKQuantitySample] {
                    self?.log("Fetched \(quantitySamples.count) background samples for \(typeName).")
                    var records = [GenericRecordDto]()
                    for sample in quantitySamples {
                        let value: Double
                        if typeName == "HeartRate" || typeName == "RestingHeartRate" {
                            value = sample.quantity.doubleValue(for: HKUnit(from: "count/min"))
                        } else if typeName == "StepCount" || typeName == "FlightsClimbed" || typeName == "PushCount" {
                            value = sample.quantity.doubleValue(for: HKUnit.count())
                        } else if typeName == "ActiveEnergyBurned" || typeName == "BasalEnergyBurned" || typeName == "DietaryEnergy" {
                            value = sample.quantity.doubleValue(for: .kilocalorie())
                        } else if typeName == "DistanceWalkingRunning" || typeName == "DistanceCycling" || typeName == "DistanceSwimming" || typeName == "DistanceWheelchair" || typeName == "Height" {
                            value = sample.quantity.doubleValue(for: .meter())
                        } else if typeName == "BodyMass" || typeName == "LeanBodyMass" {
                            value = sample.quantity.doubleValue(for: .gramUnit(with: .kilo))
                        } else if typeName == "DietaryCarbs" || typeName == "DietaryProtein" || typeName == "DietaryFat" {
                            value = sample.quantity.doubleValue(for: .gram())
                        } else if typeName == "RespiratoryRate" {
                            value = sample.quantity.doubleValue(for: HKUnit(from: "count/min"))
                        } else if typeName == "VO2Max" {
                            value = sample.quantity.doubleValue(for: HKUnit(from: "ml/kg*min"))
                        } else if typeName == "BodyFatPercentage" || typeName == "OxygenSaturation" {
                            value = sample.quantity.doubleValue(for: .percent())
                        } else if typeName == "BodyTemperature" || typeName == "BasalBodyTemperature" {
                            value = sample.quantity.doubleValue(for: .degreeCelsius())
                        } else if typeName == "BloodGlucose" {
                            value = sample.quantity.doubleValue(for: HKUnit(from: "mg/dL"))
                        } else if typeName == "BloodPressureSystolic" || typeName == "BloodPressureDiastolic" {
                            value = sample.quantity.doubleValue(for: .millimeterOfMercury())
                        } else if typeName == "DietaryWater" {
                            value = sample.quantity.doubleValue(for: .liter())
                        } else {
                            value = 0
                        }
                        
                        records.append(GenericRecordDto(
                            uuid: sample.uuid.uuidString,
                            type: typeName,
                            startTime: Int64(sample.startDate.timeIntervalSince1970 * 1000),
                            endTime: Int64(sample.endDate.timeIntervalSince1970 * 1000),
                            value: value
                        ))
                    }
                    backgroundRecords.append(contentsOf: records)
                }
                group.leave()
            }
            healthStore.execute(query)
        }
        
        group.notify(queue: .main) {
            completion(backgroundRecords.get())
        }
    }
    
    private func fetchSamplesForWorkout(workout: HKWorkout, completion: @escaping @Sendable ([HeartRateDto], [DistanceDto], [CadenceDto], [SpeedDto], [LapDto]) -> Void) {
        let predicate = HKQuery.predicateForSamples(withStart: workout.startDate, end: workout.endDate, options: .strictStartDate)
        
        let hrType = HKQuantityType.quantityType(forIdentifier: .heartRate)!
        let stepType = HKQuantityType.quantityType(forIdentifier: .stepCount)!
        let speedType = HKQuantityType.quantityType(forIdentifier: .runningSpeed)!
        
        var hrSamplesDto: [HeartRateDto] = []
        var distSamplesDto: [DistanceDto] = []
        var cadSamplesDto: [CadenceDto] = []
        var speedSamplesDto: [SpeedDto] = []
        var lapSamplesDto: [LapDto] = []
        
        if let events = workout.workoutEvents {
            for event in events {
                if event.type == .lap || event.type == .segment {
                    let start = Int64(event.dateInterval.start.timeIntervalSince1970 * 1000)
                    let end = Int64(event.dateInterval.end.timeIntervalSince1970 * 1000)
                    lapSamplesDto.append(LapDto(startTime: start, endTime: end))
                }
            }
        }
        
        let group = DispatchGroup()
        
        group.enter()
        let hrQuery = HKSampleQuery(sampleType: hrType, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { _, samples, _ in
            if let samples = samples as? [HKQuantitySample] {
                hrSamplesDto = samples.map { HeartRateDto(time: Int64($0.startDate.timeIntervalSince1970 * 1000), bpm: $0.quantity.doubleValue(for: HKUnit(from: "count/min"))) }
            }
            group.leave()
        }
        healthStore.execute(hrQuery)
        
        group.enter()
        let stepQuery = HKSampleQuery(sampleType: stepType, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { _, samples, _ in
            if let samples = samples as? [HKQuantitySample] {
                for sample in samples {
                    let durationInMinutes = sample.endDate.timeIntervalSince(sample.startDate) / 60.0
                    if durationInMinutes > 0 {
                        let steps = sample.quantity.doubleValue(for: HKUnit.count())
                        let rpm = steps / durationInMinutes
                        cadSamplesDto.append(CadenceDto(time: Int64(sample.startDate.timeIntervalSince1970 * 1000), rpm: rpm))
                    }
                }
            }
            group.leave()
        }
        healthStore.execute(stepQuery)
        
        group.enter()
        let speedQuery = HKSampleQuery(sampleType: speedType, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { _, samples, _ in
            if let samples = samples as? [HKQuantitySample] {
                speedSamplesDto = samples.map { SpeedDto(time: Int64($0.startDate.timeIntervalSince1970 * 1000), speed: $0.quantity.doubleValue(for: .meter().unitDivided(by: .second()))) }
            }
            group.leave()
        }
        healthStore.execute(speedQuery)
        
        group.notify(queue: .main) {
            completion(hrSamplesDto, distSamplesDto, cadSamplesDto, speedSamplesDto, lapSamplesDto)
        }
    }
}

// Data structures
struct BulkSyncPayload: Codable {
    let records: [GenericRecordDto]
    let workouts: [WorkoutDto]
}

struct GenericRecordDto: Codable {
    let uuid: String
    let type: String
    let startTime: Int64
    let endTime: Int64
    let value: Double
}

struct WorkoutDto: Codable {
    let uuid: String
    let activityType: Int
    let startTime: Int64
    let endTime: Int64
    let totalEnergyBurned: Double
    let totalDistance: Double
    let heartRates: [HeartRateDto]
    let distances: [DistanceDto]
    let laps: [LapDto]
    let cadences: [CadenceDto]
    let speeds: [SpeedDto]
}

struct LapDto: Codable {
    let startTime: Int64
    let endTime: Int64
}

struct CadenceDto: Codable {
    let time: Int64
    let rpm: Double
}

struct SpeedDto: Codable {
    let time: Int64
    let speed: Double
}

struct DistanceDto: Codable {
    let time: Int64
    let meters: Double
}

struct StepsDto: Codable {
    let startTime: Int64
    let endTime: Int64
    let count: Int64
}

struct HeartRateDto: Codable {
    let time: Int64
    let bpm: Double
}

struct WeightDto: Codable {
    let time: Int64
    let kg: Double
}
