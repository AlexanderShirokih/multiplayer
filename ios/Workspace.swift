import ProjectDescription

let workspace = Workspace(
    name: "MultiPlayer",
    projects: [
        "Core/Domain",
        "Core/Data",
        "Core/UI",
        "Core/Player",
        "Feature/Auth",
        "Feature/Library",
        "Services/DeviceMusic",
        "Services/YandexMusic",
        "Services/KitharaPlayer",
        "App"
    ]
)
