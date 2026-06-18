import SwiftUI

struct ContentView: View {
    @State private var ipAddress: String = ""
    @State private var syncDays: String = "30"
    @State private var pingStatus: String = "Not connected"
    @StateObject private var healthManager = HealthKitManager.shared
    
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "heart.fill")
                .imageScale(.large)
                .foregroundStyle(.red)
            Text("Health Sync iOS")
                .font(.headline)
            
            TextField("Android IP Address", text: $ipAddress)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .keyboardType(.numbersAndPunctuation)
                .padding(.horizontal)
                
            HStack {
                Text("Sync history (days):")
                TextField("Days", text: $syncDays)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .keyboardType(.numberPad)
                    .frame(width: 80)
            }
            .padding(.horizontal)
            
            Button("Ping Server") {
                NetworkService.shared.androidIP = ipAddress
                pingStatus = "Pinging..."
                NetworkService.shared.pingAndroidServer { success in
                    DispatchQueue.main.async {
                        pingStatus = success ? "Success!" : "Failed"
                    }
                }
            }
            .buttonStyle(.borderedProminent)
            
            Text("Status: \(pingStatus)")
                .foregroundColor(pingStatus == "Success!" ? .green : .secondary)
            
            Spacer().frame(height: 20)
            
            Button("Authorize HealthKit") {
                healthManager.requestAuthorization { success in
                    print("HealthKit Auth: \(success)")
                }
            }
            
            if healthManager.isSyncing {
                ProgressView("Syncing...")
            }
            
            Button("Sync All Data") {
                let days = Int(syncDays) ?? 30
                healthManager.fetchAllData(forLastDays: days) { payload in
                    guard let payload = payload else { return }
                    healthManager.log("Uploading to Android server...")
                    NetworkService.shared.sendData(endpoint: "sync/bulk", payload: payload) { success in
                        healthManager.log(success ? "Upload Success!" : "Upload Failed.")
                        DispatchQueue.main.async { healthManager.isSyncing = false }
                    }
                }
            }
            .buttonStyle(.bordered)
            .disabled(healthManager.isSyncing)
            
            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(healthManager.syncLogs.indices, id: \.self) { index in
                        Text(healthManager.syncLogs[index])
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(height: 150)
            .padding()
            .background(Color(.systemGray6))
            .cornerRadius(8)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
