import ProjectDescription
import ProjectDescriptionHelpers

let kitharaPath = KitharaDirectory.packagePath()

let project = Project(
    name: "ServicesKitharaPlayer",
    packages: [
        .local(path: kitharaPath)
    ],
    targets: [
        .target(
            name: "ServicesKitharaPlayer",
            destinations: .iOS,
            product: .framework,
            bundleId: "com.mplayeraudio.service.kitharaplayer",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Sources/**"],
            dependencies: [
                .project(target: "CoreDomain", path: "../../Core/Domain"),
                .project(target: "CorePlayer", path: "../../Core/Player"),
                .package(product: "Kithara", type: .runtime)
            ],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0"
                ]
            )
        ),
        .target(
            name: "ServicesKitharaPlayerTests",
            destinations: .iOS,
            product: .unitTests,
            bundleId: "com.mplayeraudio.service.kitharaplayer.tests",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Tests/**"],
            dependencies: [
                .target(name: "ServicesKitharaPlayer"),
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
