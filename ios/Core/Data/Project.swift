import ProjectDescription

let project = Project(
    name: "CoreDataLayer",
    targets: [
        .target(
            name: "CoreDataLayer",
            destinations: .iOS,
            product: .framework,
            bundleId: "com.mplayeraudio.core.data",
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
        ),
        .target(
            name: "CoreDataLayerTests",
            destinations: .iOS,
            product: .unitTests,
            bundleId: "com.mplayeraudio.core.data.tests",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Tests/**"],
            dependencies: [
                .target(name: "CoreDataLayer"),
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
