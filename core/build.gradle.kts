// Platform-free logic. It must never depend on Bukkit, Paper, Adventure or Brigadier, not even in tests.

// The version paper-api brings. The server provides it at runtime, so the plugin jar does not bundle it.
val snakeYaml = "org.yaml:snakeyaml:2.2"

dependencies {
    "compileOnly"(snakeYaml)
    "testImplementation"(snakeYaml)
}
