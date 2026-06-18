import WatchConnectivity
import os.log

final class WatchSessionManager: NSObject, WCSessionDelegate, @unchecked Sendable {
    static let shared = WatchSessionManager()
    private let logger = OSLog(subsystem: "com.bulbadyshka.HealthSync", category: "WCSession")
    
    private override init() {
        super.init()
    }
    
    func startSession() {
        if WCSession.isSupported() {
            let session = WCSession.default
            session.delegate = self
            session.activate()
        }
    }
    
    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if let error = error {
            os_log("WCSession activation failed: %{public}@", log: self.logger, type: .error, error.localizedDescription)
            return
        }
        os_log("WCSession activated with state: %d", log: self.logger, type: .info, activationState.rawValue)
    }
    
    func sendHeartRateData(_ bpm: Double) {
        guard WCSession.default.activationState == .activated else { return }
        
        let payload: [String: Any] = [
            "type": "heartRate",
            "time": Int64(Date().timeIntervalSince1970 * 1000),
            "bpm": bpm
        ]
        
        // Use transferUserInfo to ensure delivery even if iOS app is suspended
        WCSession.default.transferUserInfo(payload)
    }
}
