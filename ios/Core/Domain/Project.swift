import ProjectDescription

let project = Project(
    name: "CoreDomain",
    targets: [
        .target(
            name: "CoreDomain",
            destinations: .iOS,
            product: .framework,
            bundleId: "com.mplayeraudio.core.domain",
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
