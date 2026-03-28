// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "LibraryFeature",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        .library(
            name: "LibraryFeature",
            targets: ["LibraryFeature"]
        )
    ],
    dependencies: [
        .package(path: "../../Core/Domain"),
        .package(path: "../../Core/UI"),
        .package(path: "../../Core/Player"),
        .package(url: "https://github.com/realm/SwiftLint", from: "0.57.0")
    ],
    targets: [
        .target(
            name: "LibraryFeature",
            dependencies: [
                .product(name: "CoreDomain", package: "Domain"),
                .product(name: "CoreUI", package: "UI"),
                .product(name: "CorePlayer", package: "Player")
            ],
            plugins: [
                .plugin(name: "SwiftLintBuildToolPlugin", package: "SwiftLint")
            ]
        ),
        .testTarget(
            name: "LibraryFeatureTests",
            dependencies: [
                "LibraryFeature",
                .product(name: "CoreDomain", package: "Domain"),
                .product(name: "CorePlayer", package: "Player")
            ],
            plugins: [
                .plugin(name: "SwiftLintBuildToolPlugin", package: "SwiftLint")
            ]
        )
    ]
)
