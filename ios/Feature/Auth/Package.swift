// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "AuthFeature",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(
            name: "AuthFeature",
            targets: ["AuthFeature"]
        ),
    ],
    targets: [
        .target(
            name: "AuthFeature"
        ),
        .testTarget(
            name: "AuthFeatureTests",
            dependencies: ["AuthFeature"]
        ),
    ]
)
