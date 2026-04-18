import ProjectDescription

let project = Project(
    name: "DeviceMusicService",
    targets: [
        .target(
            name: "DeviceMusicService",
            destinations: .iOS,
            product: .framework,
            bundleId: "com.mplayeraudio.service.devicemusic",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Sources/**"],
            dependencies: [
                .project(target: "CoreDomain", path: "../../Core/Domain")
            ],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0"
                ]
            )
        ),
        .target(
            name: "DeviceMusicServiceTests",
            destinations: .iOS,
            product: .unitTests,
            bundleId: "com.mplayeraudio.service.devicemusic.tests",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Tests/**"],
            dependencies: [
                .target(name: "DeviceMusicService"),
                .project(target: "CoreDomain", path: "../../Core/Domain")
            ],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0"
                ]
            )
        )
    ]
)
