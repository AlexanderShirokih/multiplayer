import ProjectDescription

let project = Project(
    name: "NowPlayingService",
    targets: [
        .target(
            name: "NowPlayingService",
            destinations: .iOS,
            product: .framework,
            bundleId: "com.mplayeraudio.service.nowplaying",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Sources/**"],
            dependencies: [
                .project(target: "CoreDomain", path: "../../Core/Domain"),
                .project(target: "CorePlayer", path: "../../Core/Player")
            ],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0"
                ]
            )
        ),
        .target(
            name: "NowPlayingServiceTests",
            destinations: .iOS,
            product: .unitTests,
            bundleId: "com.mplayeraudio.service.nowplaying.tests",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Tests/**"],
            dependencies: [
                .target(name: "NowPlayingService"),
                .project(target: "CoreDomain", path: "../../Core/Domain"),
                .project(target: "CorePlayer", path: "../../Core/Player")
            ],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0"
                ]
            )
        )
    ]
)
