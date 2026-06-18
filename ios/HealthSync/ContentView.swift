import SwiftUI

struct ContentView: View {
    @State private var ipAddress: String = ""
    @State private var syncDays: String = "30"
    @State private var pingStatus: String = "Not connected"
    @State private var isPinging: Bool = false
    @StateObject private var healthManager = HealthKitManager.shared
    
    var body: some View {
        ZStack {
            AmbientBackgroundView()
            
            VStack(spacing: 24) {
                // Header
                VStack(spacing: 12) {
                    Image(systemName: "heart.circle.fill")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 64, height: 64)
                        .foregroundColor(.pink)
                        .shadow(color: .pink.opacity(0.5), radius: 10, x: 0, y: 5)
                    
                    Text("Health Sync")
                        .font(.system(size: 34, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                }
                .padding(.top, 20)
                
                // Configuration Card
                VStack(spacing: 16) {
                    HStack {
                        Image(systemName: "network")
                            .foregroundColor(.gray)
                        TextField("Android IP Address", text: $ipAddress)
                            .keyboardType(.numbersAndPunctuation)
                            .foregroundColor(.white)
                            .preferredColorScheme(.dark)
                    }
                    .padding()
                    .background(Color.white.opacity(0.08))
                    .cornerRadius(14)
                    
                    HStack {
                        Image(systemName: "clock.arrow.circlepath")
                            .foregroundColor(.gray)
                        Text("History (days):")
                            .foregroundColor(.white)
                        Spacer()
                        TextField("30", text: $syncDays)
                            .keyboardType(.numberPad)
                            .foregroundColor(.white)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 60)
                            .preferredColorScheme(.dark)
                    }
                    .padding()
                    .background(Color.white.opacity(0.08))
                    .cornerRadius(14)
                    
                    Button(action: pingServer) {
                        HStack {
                            if isPinging {
                                ProgressView().tint(.white)
                            } else {
                                Image(systemName: "antenna.radiowaves.left.and.right")
                            }
                            Text(isPinging ? "Pinging..." : "Ping Server")
                                .font(.system(size: 17, weight: .semibold, design: .rounded))
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(14)
                        .shadow(color: .blue.opacity(0.4), radius: 8, x: 0, y: 4)
                    }
                    .disabled(isPinging)
                    
                    HStack(spacing: 6) {
                        Circle()
                            .fill(statusColor)
                            .frame(width: 8, height: 8)
                        Text(pingStatus)
                            .font(.subheadline)
                            .foregroundColor(Color.white.opacity(0.7))
                    }
                }
                .padding()
                .background(.ultraThinMaterial)
                .cornerRadius(28)
                .shadow(color: .black.opacity(0.3), radius: 20, x: 0, y: 10)
                
                // Action Card
                VStack(spacing: 16) {
                    Button(action: {
                        healthManager.requestAuthorization { success in
                            print("HealthKit Auth: \(success)")
                        }
                    }) {
                        HStack {
                            Image(systemName: "lock.shield")
                            Text("Authorize HealthKit")
                                .font(.system(size: 17, weight: .medium, design: .rounded))
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.white.opacity(0.1))
                        .foregroundColor(.white)
                        .cornerRadius(14)
                    }
                    
                    Button(action: syncData) {
                        HStack {
                            if healthManager.isSyncing {
                                ProgressView().tint(.white)
                            } else {
                                Image(systemName: "arrow.triangle.2.circlepath")
                            }
                            Text(healthManager.isSyncing ? "Syncing Data..." : "Sync All Data")
                                .font(.system(size: 17, weight: .bold, design: .rounded))
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.pink)
                        .foregroundColor(.white)
                        .cornerRadius(14)
                        .shadow(color: .pink.opacity(0.4), radius: 8, x: 0, y: 4)
                    }
                    .disabled(healthManager.isSyncing)
                }
                .padding()
                .background(.ultraThinMaterial)
                .cornerRadius(28)
                .shadow(color: .black.opacity(0.3), radius: 20, x: 0, y: 10)
                
                // Logs Section
                VStack(alignment: .leading, spacing: 8) {
                    Text("ACTIVITY LOGS")
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(Color.white.opacity(0.5))
                        .padding(.horizontal, 4)
                    
                    ScrollView {
                        VStack(alignment: .leading, spacing: 8) {
                            if healthManager.syncLogs.isEmpty {
                                Text("No activity yet.")
                                    .font(.system(size: 13, design: .monospaced))
                                    .foregroundColor(.white.opacity(0.4))
                            } else {
                                ForEach(healthManager.syncLogs.indices, id: \.self) { index in
                                    Text("• \(healthManager.syncLogs[index])")
                                        .font(.system(size: 13, design: .monospaced))
                                        .foregroundColor(.white.opacity(0.8))
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                    }
                    .background(Color.black.opacity(0.4))
                    .cornerRadius(16)
                }
                .frame(maxHeight: .infinity)
            }
            .padding(20)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            hideKeyboard()
        }
    }
    
    private var statusColor: Color {
        switch pingStatus {
        case "Success!": return .green
        case "Failed": return .red
        case "Pinging...": return .yellow
        default: return .gray
        }
    }
    
    private func pingServer() {
        var cleanIP = ipAddress.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanIP.hasPrefix("http://") { cleanIP.removeFirst(7) }
        if cleanIP.hasPrefix("https://") { cleanIP.removeFirst(8) }
        if cleanIP.hasSuffix("/") { cleanIP.removeLast() }
        NetworkService.shared.androidIP = cleanIP
        
        pingStatus = "Pinging..."
        isPinging = true
        
        NetworkService.shared.pingAndroidServer { success in
            DispatchQueue.main.async {
                isPinging = false
                pingStatus = success ? "Success!" : "Failed"
            }
        }
    }
    
    private func syncData() {
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
}

extension View {
    func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

struct AmbientBackgroundView: View {
    @State private var animateGradient = false
    
    var body: some View {
        ZStack {
            // Dark elegant background
            Color(red: 0.07, green: 0.07, blue: 0.1)
                .ignoresSafeArea()
            
            // Animated ambient glow blobs
            Circle()
                .fill(Color.purple.opacity(0.3))
                .blur(radius: 90)
                .frame(width: 300, height: 300)
                .offset(x: animateGradient ? 100 : -100, y: animateGradient ? -150 : 0)
                .animation(.easeInOut(duration: 8).repeatForever(autoreverses: true), value: animateGradient)
            
            Circle()
                .fill(Color.blue.opacity(0.25))
                .blur(radius: 90)
                .frame(width: 250, height: 250)
                .offset(x: animateGradient ? -100 : 150, y: animateGradient ? 150 : 50)
                .animation(.easeInOut(duration: 10).repeatForever(autoreverses: true), value: animateGradient)
        }
        .drawingGroup() // Renders as a single Metal texture, preventing massive overdraw CPU usage
        .ignoresSafeArea()
        .onAppear {
            animateGradient = true
        }
    }
}

#Preview {
    ContentView()
}
