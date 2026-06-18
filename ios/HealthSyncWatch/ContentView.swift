import SwiftUI

struct ContentView: View {
    @State private var isWorkingOut = false
    
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: isWorkingOut ? "figure.run.circle.fill" : "figure.run.circle")
                .imageScale(.large)
                .foregroundStyle(isWorkingOut ? .red : .green)
            
            Text(isWorkingOut ? "Workout Active" : "Ready to Sync")
                .font(.headline)
            
            Button(isWorkingOut ? "Stop Workout" : "Start Workout") {
                if isWorkingOut {
                    WorkoutManager.shared.endWorkout()
                } else {
                    WorkoutManager.shared.startWorkout()
                }
                isWorkingOut.toggle()
            }
            .buttonStyle(.borderedProminent)
            .tint(isWorkingOut ? .red : .green)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
