// swift-tools-version: 6.0

import Foundation
import PackageDescription

guard let kitharaDir = ProcessInfo.processInfo.environment["KITHARA_DIR"], !kitharaDir.isEmpty else {
    fatalError("Set KITHARA_DIR environment variable to the Kithara repository path")
}

let package = Package(
    name: "ServicesKitharaPlayer",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        .library(
            name: "ServicesKitharaPlayer",
            targets: ["ServicesKitharaPlayer"]
        )
    ],
    dependencies: [
        .package(name: "Kithara", path: kitharaDir),
        .package(url: "https://github.com/realm/SwiftLint", from: "0.57.0")
    ],
    targets: [
        .target(
            name: "ServicesKitharaPlayer",
            dependencies: [
                .product(name: "Kithara", package: "Kithara")
            ],
            plugins: [
                .plugin(name: "SwiftLintBuildToolPlugin", package: "SwiftLint")
            ]
        )
    ]
)
