import ProjectDescription

let project = Project(
    name: "CoreUI",
    targets: [
        .target(
            name: "CoreUI",
            destinations: .iOS,
            product: .framework,
            bundleId: "com.mplayeraudio.core.ui",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Sources/**"],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0"
                ]
            )
        )
    ]
)
