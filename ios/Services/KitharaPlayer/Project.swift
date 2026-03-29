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
                .package(product: "Kithara", type: .runtime)
            ],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0"
                ]
            )
        )
    ]
)
