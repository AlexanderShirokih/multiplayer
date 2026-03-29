import ProjectDescription

let project = Project(
    name: "CorePlayer",
    targets: [
        .target(
            name: "CorePlayer",
            destinations: .iOS,
            product: .framework,
            bundleId: "com.mplayeraudio.core.player",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Sources/**"],
            dependencies: [
                .project(target: "CoreDomain", path: "../Domain")
            ],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0"
                ]
            )
        )
    ]
)
