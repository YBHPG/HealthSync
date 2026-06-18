#!/bin/sh
sed -i '' 's/SystemCapabilities = "\[\\"com.apple.HealthKit\\": \[\\"enabled\\": 1\]\]";/SystemCapabilities = { com.apple.HealthKit = { enabled = 1; }; };/g' HealthSync.xcodeproj/project.pbxproj
