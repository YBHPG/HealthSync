import HealthKit
import os.log

final class WorkoutManager: NSObject, HKLiveWorkoutBuilderDelegate, HKWorkoutSessionDelegate, @unchecked Sendable {
    static let shared = WorkoutManager()
    private let logger = OSLog(subsystem: "com.bulbadyshka.HealthSync", category: "WorkoutManager")
    
    let healthStore = HKHealthStore()
    var session: HKWorkoutSession?
    var builder: HKLiveWorkoutBuilder?
    
    private override init() {}
    
    func startWorkout() {
        let configuration = HKWorkoutConfiguration()
        configuration.activityType = .running
        configuration.locationType = .outdoor
        
        do {
            session = try HKWorkoutSession(healthStore: healthStore, configuration: configuration)
            builder = session?.associatedWorkoutBuilder()
        } catch {
            os_log("Failed to start workout session: %{public}@", log: self.logger, type: .error, error.localizedDescription)
            return
        }
        
        session?.delegate = self
        builder?.delegate = self
        builder?.dataSource = HKLiveWorkoutDataSource(healthStore: healthStore, workoutConfiguration: configuration)
        
        let startDate = Date()
        session?.startActivity(with: startDate)
        builder?.beginCollection(withStart: startDate) { success, error in
            if let error = error {
                os_log("Failed to begin collection: %{public}@", log: self.logger, type: .error, error.localizedDescription)
            }
        }
    }
    
    func endWorkout() {
        session?.end()
        builder?.endCollection(withEnd: Date()) { success, error in
            self.builder?.finishWorkout { workout, error in
                os_log("Workout finished", log: self.logger, type: .info)
            }
        }
    }
    
    // MARK: - HKLiveWorkoutBuilderDelegate
    func workoutBuilder(_ workoutBuilder: HKLiveWorkoutBuilder, didCollectDataOf collectedTypes: Set<HKSampleType>) {
        for type in collectedTypes {
            guard let quantityType = type as? HKQuantityType else { continue }
            
            if quantityType == HKQuantityType.quantityType(forIdentifier: .heartRate) {
                if let statistics = workoutBuilder.statistics(for: quantityType), let quantity = statistics.mostRecentQuantity() {
                    let heartRate = quantity.doubleValue(for: HKUnit(from: "count/min"))
                    os_log("New heart rate: %f", log: self.logger, type: .info, heartRate)
                    WatchSessionManager.shared.sendHeartRateData(heartRate)
                }
            }
        }
    }
    
    func workoutBuilderDidCollectEvent(_ workoutBuilder: HKLiveWorkoutBuilder) {}
    
    // MARK: - HKWorkoutSessionDelegate
    func workoutSession(_ workoutSession: HKWorkoutSession, didChangeTo toState: HKWorkoutSessionState, from fromState: HKWorkoutSessionState, date: Date) {}
    func workoutSession(_ workoutSession: HKWorkoutSession, didFailWithError error: Error) {}
}
