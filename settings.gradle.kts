rootProject.name = "wafflecore"

include("host")
listOf("chatguard", "modlog", "xrayalert", "perf", "daily", "jobs", "scoreboard").forEach {
    include("modules:$it")
}
