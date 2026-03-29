import ProjectDescription

let project = Project(
    name: "AuthFeature",
    targets: [
        .target(
            name: "AuthFeature",
            destinations: .iOS,
            product: .framework,
            bundleId: "com.mplayeraudio.feature.auth",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Sources/**"],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0"
                ]
            )
        ),
        .target(
            name: "AuthFeatureTests",
            destinations: .iOS,
            product: .unitTests,
            bundleId: "com.mplayeraudio.feature.auth.tests",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Tests/**"],
            dependencies: [
                .target(name: "AuthFeature")
            ],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0"
                ]
            )
        )
    ]
)
