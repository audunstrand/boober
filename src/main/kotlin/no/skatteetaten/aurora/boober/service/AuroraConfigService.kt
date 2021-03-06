package no.skatteetaten.aurora.boober.service

import no.skatteetaten.aurora.boober.mapper.AuroraConfigFields
import no.skatteetaten.aurora.boober.mapper.AuroraConfigMapper
import no.skatteetaten.aurora.boober.mapper.AuroraConfigMapper.Companion.baseHandlers
import no.skatteetaten.aurora.boober.mapper.v1.AuroraConfigMapperV1Deploy
import no.skatteetaten.aurora.boober.mapper.v1.AuroraConfigMapperV1LocalTemplate
import no.skatteetaten.aurora.boober.mapper.v1.AuroraConfigMapperV1Template
import no.skatteetaten.aurora.boober.model.AuroraConfig
import no.skatteetaten.aurora.boober.model.AuroraDeploymentConfig
import no.skatteetaten.aurora.boober.model.DeployCommand
import no.skatteetaten.aurora.boober.model.TemplateType
import no.skatteetaten.aurora.boober.service.internal.*
import no.skatteetaten.aurora.boober.service.openshift.OpenShiftClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AuroraConfigService(val openShiftClient: OpenShiftClient) {
    val logger: Logger = LoggerFactory.getLogger(AuroraConfigService::class.java)


    fun validate(auroraConfig: AuroraConfig) {

        val deployCommands = auroraConfig.getApplicationIds().map { DeployCommand(it) }
        processDeployCommands(deployCommands, {
            createMapper(it, auroraConfig).validate()
            true
        })
    }

    fun createAuroraDcs(auroraConfig: AuroraConfig, deployCommands: List<DeployCommand>): List<AuroraDeploymentConfig> {

        return processDeployCommands(deployCommands, { createAuroraDeploymentConfigs(it, auroraConfig) })
    }

    private fun <T : Any> processDeployCommands(deployCommands: List<DeployCommand>, operation: (DeployCommand) -> T): List<T> {

        return deployCommands.map { deployCommand ->
            val aid = deployCommand.applicationId
            try {
                val value = operation(deployCommand)
                Result<T, Error?>(value = value)
            } catch (e: ApplicationConfigException) {
                logger.debug("ACE {}", e.errors)
                Result<T, Error?>(error = Error(aid.application, aid.environment, e.errors))
            } catch (e: IllegalArgumentException) {
                logger.debug("IAE {}", e.message)
                Result<T, Error?>(error = Error(aid.application, aid.environment, listOf(ValidationError(e.message!!))))
            }
        }.orElseThrow {
            AuroraConfigException("AuroraConfig contained errors for one or more applications", it)
        }
    }

    fun createAuroraDeploymentConfigs(deployCommand: DeployCommand, auroraConfig: AuroraConfig): AuroraDeploymentConfig {
        val mapper = createMapper(deployCommand, auroraConfig)
        mapper.validate()
        val adc = mapper.toAuroraDeploymentConfig()
        return adc
    }


    fun createMapper(deployCommand: DeployCommand, auroraConfig: AuroraConfig): AuroraConfigMapper {

        val fields = AuroraConfigFields.create(baseHandlers, auroraConfig.getFilesForApplication(deployCommand))

        val type = fields.extract("type", { TemplateType.valueOf(it.textValue()) })

        val schemaVersion = fields.extract("schemaVersion")

        if (schemaVersion != "v1") {
            throw IllegalArgumentException("Only v1 of schema is supported")
        }

        if (type == TemplateType.localTemplate) {
            return AuroraConfigMapperV1LocalTemplate(deployCommand, auroraConfig, openShiftClient)
        }

        if (type == TemplateType.template) {
            return AuroraConfigMapperV1Template(deployCommand, auroraConfig, openShiftClient)

        }

        return AuroraConfigMapperV1Deploy(deployCommand, auroraConfig, openShiftClient)
    }
}
