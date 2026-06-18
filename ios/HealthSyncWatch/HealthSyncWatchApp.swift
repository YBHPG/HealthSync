import SwiftUI

@main
struct HealthSyncWatchApp: App {
    init() {
        WatchSessionManager.shared.startSession()
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
