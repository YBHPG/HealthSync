import SwiftUI
import WatchConnectivity
import os.log

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        // WatchConnectivity initialization removed since data is synced purely from iPhone HealthKit
        return true
    }
}

@main
struct HealthSyncApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
