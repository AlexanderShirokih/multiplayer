import ProjectDescription

let project = Project(
    name: "YandexMusicService",
    targets: [
        .target(
            name: "YandexMusicService",
            destinations: .iOS,
            product: .framework,
            bundleId: "com.mplayeraudio.service.yandexmusic",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Sources/**"],
            dependencies: [
                .project(target: "CoreDomain", path: "../../Core/Domain"),
                .project(target: "AuthFeature", path: "../../Feature/Auth")
            ],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0"
                ]
            )
        ),
        .target(
            name: "YandexMusicServiceTests",
            destinations: .iOS,
            product: .unitTests,
            bundleId: "com.mplayeraudio.service.yandexmusic.tests",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Tests/**"],
            dependencies: [
                .target(name: "YandexMusicService"),
                .project(target: "CoreDomain", path: "../../Core/Domain"),
                .project(target: "AuthFeature", path: "../../Feature/Auth")
            ],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0"
                ]
            )
        )
    ]
)
