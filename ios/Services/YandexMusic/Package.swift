// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "YandexMusicService",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(
            name: "YandexMusicService",
            targets: ["YandexMusicService"]
        ),
    ],
    dependencies: [
        .package(path: "../../Core/Domain"),
        .package(path: "../../Feature/Auth"),
    ],
    targets: [
        .target(
            name: "YandexMusicService",
            dependencies: [
                .product(name: "CoreDomain", package: "Domain"),
                .product(name: "AuthFeature", package: "Auth"),
            ]
        ),
        .testTarget(
            name: "YandexMusicServiceTests",
            dependencies: [
                "YandexMusicService",
                .product(name: "CoreDomain", package: "Domain"),
                .product(name: "AuthFeature", package: "Auth"),
            ]
        ),
    ]
)
