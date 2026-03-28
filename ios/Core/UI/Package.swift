// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "CoreUI",
    platforms: [
        .iOS(.v17)
    ],
    products: [
        .library(
            name: "CoreUI",
            targets: ["CoreUI"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/realm/SwiftLint", from: "0.57.0")
    ],
    targets: [
        .target(
            name: "CoreUI",
            plugins: [
                .plugin(name: "SwiftLintBuildToolPlugin", package: "SwiftLint")
            ]
        )
    ]
)
