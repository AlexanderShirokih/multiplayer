import ProjectDescription

let project = Project(
    name: "MultiPlayer",
    targets: [
        .target(
            name: "MultiPlayer",
            destinations: .iOS,
            product: .app,
            bundleId: "com.mplayeraudio",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .file(path: "Resources/Info.plist"),
            sources: ["Sources/**"],
            resources: [
                "Resources/Assets.xcassets",
                "Resources/Preview Content/**"
            ],
            dependencies: [
                .project(target: "CoreUI", path: "../Core/UI"),
                .project(target: "AuthFeature", path: "../Feature/Auth"),
                .project(target: "LibraryFeature", path: "../Feature/Library"),
                .project(target: "YandexMusicService", path: "../Services/YandexMusic"),
                .project(target: "ServicesKitharaPlayer", path: "../Services/KitharaPlayer")
            ],
            settings: .settings(
                base: [
                    "SWIFT_VERSION": "5.0",
                    "GENERATE_INFOPLIST_FILE": "NO",
                    "INFOPLIST_FILE": "Resources/Info.plist",
                    "ASSETCATALOG_COMPILER_APPICON_NAME": "AppIcon",
                    "ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME": "",
                    "CODE_SIGN_STYLE": "Automatic",
                    "CURRENT_PROJECT_VERSION": "1",
                    "DEVELOPMENT_ASSET_PATHS": "\"Resources/Preview Content\"",
                    "ENABLE_PREVIEWS": "YES",
                    "MARKETING_VERSION": "1.0",
                    "PRODUCT_BUNDLE_IDENTIFIER": "com.mplayeraudio",
                    "PRODUCT_NAME": "MultiPlayer",
                    "SUPPORTED_PLATFORMS": "iphoneos iphonesimulator",
                    "SWIFT_EMIT_LOC_STRINGS": "YES",
                    "TARGETED_DEVICE_FAMILY": "1,2"
                ],
                configurations: [
                    .debug(
                        name: "Debug",
                        xcconfig: "./Configs/Debug.xcconfig"
                    ),
                    .release(
                        name: "Release",
                        xcconfig: "./Configs/Release.xcconfig"
                    )
                ]
            )
        )
    ]
)
