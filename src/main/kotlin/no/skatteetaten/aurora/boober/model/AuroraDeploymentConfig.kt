package no.skatteetaten.aurora.boober.model

import javax.validation.Valid
import javax.validation.constraints.Pattern
import javax.validation.constraints.Size

enum class TemplateType {
    deploy, development, process,
}

class AuroraDeploymentConfig(
        val cluster: String,
        val envName: String,

        @get:Pattern(message = "Only lowercase letters, max 24 length", regexp = "^[a-z]{0,23}[a-z]$")
        val affiliation: String,

        @get:Pattern(message = "Must be valid DNSDNS952 label", regexp = "^[a-z][-a-z0-9]{0,23}[a-z0-9]$")
        val name: String,

        val groups: String,
        val users: String,
        val type: TemplateType,
        val replicas: Int,
        val flags: List<String>,
        val secretFile: String?,
        val config: Map<String, String>,
        val deployDescriptor: Any
) {
    @get:Pattern(message = "Alphanumeric and dashes. Cannot end or start with dash", regexp = "^[a-z0-9][-a-z0-9]*[a-z0-9]$")
    val namespace: String
        get() = "$affiliation$envName"

    val routeName: String
        get() = "http://$name-$namespace.$cluster.paas.skead.no"

    val configLine: String
        get() = config.map { "${it.key}=${it.value}" }.joinToString(separator = "\\n", transform = { it })

    val schemaVersion: String
        get() = "v1"

    val route: Boolean
        get() = flags.contains("route")

    val rolling: Boolean
        get() = flags.contains("rolling")
}

data class TemplateDeploy(
        val templateFile: String? = null,
        val template: String? = null,
        val parameters: Map<String, String> = mapOf()
)

data class AuroraDeploy(
        @get:Valid
        val build: ConfigBuild,
        val deploy: ConfigDeploy = ConfigDeploy(),
        val cert: String
) {
    val dockerGroup: String = build.groupId.replace(".", "_")
    val dockerName: String = build.artifactId
}

data class ConfigBuild(

        @get:Size(min = 1, max = 50)
        val artifactId: String,

        @get:Size(min = 1, max = 200)
        val groupId: String,

        @get:Size(min = 1)
        val version: String,

        val extraTags: String = "latest,major,minor,patch"
)

data class ConfigDeploy(
        val splunkIndex: String = "",
        val maxMemory: String = "256Mi",
        val database: String = "",
        val certificate: String = "",
        val tag: String = "default",
        val cpuRequest: String = "0",
        val websealRoute: String = "",
        val websealRoles: String = "",
        val prometheus: Boolean = false,
        val prometheusPort: Int = 8080,
        val prometheusPath: String = "/prometheus",
        val managementPath: String = "",
        val debug: Boolean = false,
        val alarm: Boolean = true
)