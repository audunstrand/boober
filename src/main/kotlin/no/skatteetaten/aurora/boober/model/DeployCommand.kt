package no.skatteetaten.aurora.boober.model

data class DeployCommand @JvmOverloads constructor(
        val applicationId: ApplicationId,
        private val overrideFiles: List<AuroraConfigFile> = listOf()
) {
    val requiredFilesForApplication = setOf(
            "about.json",
            "${applicationId.application}.json",
            "${applicationId.environment}/about.json",
            "${applicationId.environment}/${applicationId.application}.json")

    val overrides = requiredFilesForApplication.mapNotNull { fileName -> overrideFiles.find { it.name == fileName } }
}