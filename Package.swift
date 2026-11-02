// swift-tools-version:5.9
// Swift Package Manager manifest for the iOS SDK. The release workflow (.github/workflows/release.yml) builds
// FeatureFlags.xcframework, uploads it to the GitHub release and rewrites `version` and `checksum` below before tagging.
import PackageDescription

let version = "0.0.0"
let checksum = "0000000000000000000000000000000000000000000000000000000000000000"

let package = Package(
    name: "FeatureFlags",
    platforms: [.iOS(.v13)],
    products: [
        .library(name: "FeatureFlags", targets: ["FeatureFlags"]),
    ],
    targets: [
        .binaryTarget(
            name: "FeatureFlags",
            url: "https://github.com/keshavop/feature-flag-SDK/releases/download/v\(version)/FeatureFlags.xcframework.zip",
            checksum: checksum
        ),
    ]
)
