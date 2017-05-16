package no.skatteetaten.aurora.boober.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.fge.jsonpatch.JsonPatch
import no.skatteetaten.aurora.boober.model.AuroraConfig
import no.skatteetaten.aurora.boober.model.AuroraConfigFile
import no.skatteetaten.aurora.boober.service.openshift.OpenShiftClient
import org.eclipse.jgit.api.Git
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import kotlin.system.measureTimeMillis

@Service
class AuroraConfigService(
        val gitService: GitService,
        val openShiftClient: OpenShiftClient,
        val mapper: ObjectMapper,
        val encryptionService: EncryptionService) {

    private val GIT_SECRET_FOLDER = ".secret"
    private val logger = LoggerFactory.getLogger(AuroraConfigService::class.java)

    fun save(affiliation: String, auroraConfig: AuroraConfig) {

        gitService.saveFilesAndClose(affiliation, prepareFilesForSave(auroraConfig))
    }

    fun save(repo: Git, auroraConfig: AuroraConfig) {

        gitService.saveFilesAndClose(repo, prepareFilesForSave(auroraConfig))
    }

    fun patchAuroraConfigFile(filename: String, auroraConfig: AuroraConfig, jsonPatchOp: String): JsonNode {

        val patch: JsonPatch = mapper.readValue(jsonPatchOp, JsonPatch::class.java)

        val auroraConfigFile = auroraConfig.auroraConfigFiles.filter { it.name == filename }.first()
        val originalContentsNode = mapper.convertValue(auroraConfigFile.contents, JsonNode::class.java)

        return patch.apply(originalContentsNode)
    }


    fun updateAuroraConfigFile(affiliation: String, filename: String, fileContents: JsonNode) {
        withAuroraConfig(affiliation, true, { auroraConfig: AuroraConfig ->
            auroraConfig.updateFile(filename, fileContents)
        })
    }

    fun findAuroraConfig(affiliation: String): AuroraConfig {

        return withAuroraConfig(affiliation, false, function = { it })
    }

    fun withAuroraConfig(affiliation: String,
                         commitChanges: Boolean = true,
                         function: (AuroraConfig) -> AuroraConfig = { it -> it }): AuroraConfig {

        val startCheckout = System.currentTimeMillis()
        val repo = gitService.checkoutRepoForAffiliation(affiliation)
        logger.debug("Spent {} millis checking out gir repository", System.currentTimeMillis() - startCheckout)

        val filesForAffiliation: Map<String, File> = gitService.getAllFilesInRepo(repo)
        val auroraConfig = createAuroraConfigFromFiles(filesForAffiliation)

        val newAuroraConfig = function(auroraConfig)

        if (commitChanges) {
            measureTimeMillis {
                save(repo, newAuroraConfig)
            }.let { logger.debug("Spent {} millis committing and pushing to git", it) }
        } else {
            measureTimeMillis {
                gitService.closeRepository(repo)
            }.let { logger.debug("Spent {} millis closing git repository", it) }
        }

        return newAuroraConfig
    }

    private fun createAuroraConfigFromFiles(filesForAffiliation: Map<String, File>): AuroraConfig {

        val secretFiles: Map<String, String> = filesForAffiliation
                .filter { it.key.startsWith(GIT_SECRET_FOLDER) }
                .map { it.key to encryptionService.decrypt(it.value.readText()) }.toMap()

        val auroraConfigFiles = filesForAffiliation
                .filter { !it.key.startsWith(GIT_SECRET_FOLDER) }
                .map { AuroraConfigFile(it.key, mapper.readValue(it.value)) }

        return AuroraConfig(auroraConfigFiles = auroraConfigFiles, secrets = secretFiles)
    }

    private fun prepareFilesForSave(auroraConfig: AuroraConfig): Map<String, String> {

        val secrets = convertSecretFilesToString(auroraConfig)

        val encryptedSecrets = secrets.map { it.key to encryptionService.encrypt(it.value) }.toMap()

        return convertFilesToString(auroraConfig) + encryptedSecrets
    }

    private fun convertFilesToString(auroraConfig: AuroraConfig): Map<String, String> {

        return auroraConfig.auroraConfigFiles.map {
            it.name to mapper.writerWithDefaultPrettyPrinter().writeValueAsString(it.contents)
        }.toMap()
    }

    private fun convertSecretFilesToString(auroraConfig: AuroraConfig): Map<String, String> {

        return auroraConfig.secrets.map {
            val secretPath = it.key.split("/")
                    .takeIf { it.size >= 2 }
                    ?.let { it.subList(it.size - 2, it.size) }
                    ?.joinToString("/") ?: it.key

            val secretFolder = secretPath.split("/")[0]
            val gitSecretFolder = "$GIT_SECRET_FOLDER/$secretPath".replace("//", "/")

            auroraConfig.auroraConfigFiles
                    .filter { it.contents.has("secretFolder") }
                    .filter { it.contents.get("secretFolder").asText().contains(secretFolder) }
                    .forEach {
                        val folder = secretPath.split("/")[0]
                        (it.contents as ObjectNode).put("secretFolder", "$GIT_SECRET_FOLDER/$folder")
                    }

            gitSecretFolder to it.value
        }.toMap()
    }
}
