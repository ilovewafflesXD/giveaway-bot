rootProject.name = "wafflecore"

include("host", "core")
listOf("chatguard", "modlog", "xrayalert", "perf", "daily", "jobs", "scoreboard").forEach {
    include("modules:$it")
}
