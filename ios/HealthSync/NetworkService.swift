import Foundation
import os.log

final class NetworkService: @unchecked Sendable {
    static let shared = NetworkService()
    
    private let logger = OSLog(subsystem: "com.bulbadyshka.HealthSync", category: "Network")
    private var session: URLSession!
    
    var androidIP: String = "" {
        didSet {
            os_log("Android IP updated: %{public}@", log: self.logger, type: .info, androidIP)
        }
    }
    
    private init() {
        let config = URLSessionConfiguration.default
        config.waitsForConnectivity = true
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 300
        self.session = URLSession(configuration: config)
    }
    
    func pingAndroidServer(completion: @escaping @Sendable (Bool) -> Void) {
        guard !androidIP.isEmpty, let url = URL(string: "http://\(androidIP):8080/api/ping") else {
            completion(false)
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        
        let task = session.dataTask(with: request) { data, response, error in
            if let error = error {
                os_log("Ping failed: %{public}@", log: self.logger, type: .error, error.localizedDescription)
                completion(false)
                return
            }
            
            if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 {
                os_log("Ping successful!", log: self.logger, type: .info)
                completion(true)
            } else {
                completion(false)
            }
        }
        task.resume()
    }
    
    func sendData<T: Encodable>(endpoint: String, payload: T, retryCount: Int = 3, completion: @escaping @Sendable (Bool) -> Void) {
        guard !androidIP.isEmpty, let url = URL(string: "http://\(androidIP):8080/api/\(endpoint)") else {
            completion(false)
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        do {
            let data = try JSONEncoder().encode(payload)
            request.httpBody = data
        } catch {
            os_log("Encoding error: %{public}@", log: self.logger, type: .error, error.localizedDescription)
            completion(false)
            return
        }
        
        performRequestWithRetry(request: request, retriesLeft: retryCount, completion: completion)
    }
    
    private func performRequestWithRetry(request: URLRequest, retriesLeft: Int, completion: @escaping @Sendable (Bool) -> Void) {
        let task = session.dataTask(with: request) { [weak self] data, response, error in
            guard let self = self else { return }
            
            if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode >= 200 && httpResponse.statusCode < 300 {
                completion(true)
                return
            }
            
            if retriesLeft > 0 {
                os_log("Request failed, retrying... (%d retries left)", log: self.logger, type: .error, retriesLeft)
                let delay = pow(2.0, Double(3 - retriesLeft)) // Exponential backoff
                DispatchQueue.global().asyncAfter(deadline: .now() + delay) {
                    self.performRequestWithRetry(request: request, retriesLeft: retriesLeft - 1, completion: completion)
                }
            } else {
                os_log("Max retries reached. Request failed.", log: self.logger, type: .error)
                completion(false)
            }
        }
        task.resume()
    }
}
