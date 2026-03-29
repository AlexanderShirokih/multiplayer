import ProjectDescription

let project = Project(
    name: "LibraryFeature",
    targets: [
        .target(
            name: "LibraryFeature",
            destinations: .iOS,
            product: .framework,
            bundleId: "com.mplayeraudio.feature.library",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Sources/**"],
            dependencies: [
                .project(target: "CoreDomain", path: "../../Core/Domain"),
                .project(target: "CoreUI", path: "../../Core/UI"),
                .project(target: "CorePlayer", path: "../../Core/Player")
            ],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0"
                ]
            )
        ),
        .target(
            name: "LibraryFeatureTests",
            destinations: .iOS,
            product: .unitTests,
            bundleId: "com.mplayeraudio.feature.library.tests",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Tests/**"],
            dependencies: [
                .target(name: "LibraryFeature"),
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
