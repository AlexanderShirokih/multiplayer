import ProjectDescription

let workspace = Workspace(
    name: "MultiPlayer",
    projects: [
        "Core/Domain",
        "Core/UI",
        "Core/Player",
        "Feature/Auth",
        "Feature/Library",
        "Services/YandexMusic",
        "Services/KitharaPlayer",
        "App"
    ]
)
