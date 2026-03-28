// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "CoreDomain",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        .library(
            name: "CoreDomain",
            targets: ["CoreDomain"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/realm/SwiftLint", from: "0.57.0")
    ],
    targets: [
        .target(
            name: "CoreDomain",
            plugins: [
                .plugin(name: "SwiftLintBuildToolPlugin", package: "SwiftLint")
            ]
        )
    ]
)
