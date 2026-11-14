val lintTask = tasks.register("dependencyLint") {
    group = "verification"
    description = "Verifies that this module only depends on modules allowed by the architecture layering."
    // Everything the action needs is captured by value so the task is configuration-cache safe.
    val allowed = mapOf(
        ":core:domain" to listOf(),
        ":core:application" to listOf(":core:domain"),
        ":core:presentation" to listOf(":core:domain", ":core:application"),
        ":infra:" to listOf(":core:domain", ":core:application"),
        ":ui:kit" to listOf(":core:presentation"),
        ":ui:screens" to listOf(":core:domain", ":core:application", ":core:presentation", ":ui:kit"),
        ":platform:" to listOf(":core:"),
        ":app:" to listOf(":core:", ":infra:", ":ui:", ":platform:"),
    )
    val projectPath = project.path
    val deps = project.configurations
        .filter { it.name in setOf("implementation", "api", "commonMainImplementation", "commonMainApi", "jvmMainImplementation", "androidMainImplementation") }
        .flatMap { cfg -> cfg.dependencies.withType(ProjectDependency::class.java).map { it.path } }
        .distinct()
    inputs.property("deps", deps)
    doLast {
        val rule = allowed.entries.firstOrNull { (prefix, _) -> projectPath.startsWith(prefix) } ?: error("No layering rule for $projectPath")
        val violations = deps.filter { dep -> rule.value.none { dep.startsWith(it) } }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Architecture violation in $projectPath: forbidden project dependencies $violations. " +
                    "Allowed prefixes: ${rule.value}. See AGENTS.md.",
            )
        }
    }
}

tasks.matching { it.name == "check" }.configureEach { dependsOn(lintTask) }
