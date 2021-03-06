package no.skatteetaten.aurora.boober.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import no.skatteetaten.aurora.boober.model.AuroraDeploymentConfig
import no.skatteetaten.aurora.boober.model.AuroraDeploymentConfigProcess
import no.skatteetaten.aurora.boober.model.AuroraDeploymentConfigProcessLocalTemplate
import no.skatteetaten.aurora.boober.model.AuroraDeploymentConfigProcessTemplate
import no.skatteetaten.aurora.boober.service.openshift.OpenShiftResourceClient
import org.springframework.stereotype.Service

@Service
class OpenShiftTemplateProcessor(
        val openShiftClient: OpenShiftResourceClient,
        val mapper: ObjectMapper) {


    fun generateObjects(apc: AuroraDeploymentConfigProcess): List<JsonNode> {


        val template = if (apc is AuroraDeploymentConfigProcessTemplate) {
            openShiftClient.get("template", apc.template, "openshift")?.body as ObjectNode
        } else if (apc is AuroraDeploymentConfigProcessLocalTemplate) {
            apc.templateJson as ObjectNode
        } else {
            throw IllegalArgumentException("Template or templateFile should be specified")
        }

        val adcParameters = apc.parameters ?: emptyMap()
        val adcParameterKeys = adcParameters.keys

        if (template.has("parameters")) {
            val parameters = template["parameters"]

            //mutation in progress. stay away.
            parameters
                    .filter { adcParameterKeys.contains(it["name"].textValue()) }
                    .forEach {
                        val node = it as ObjectNode
                        node.put("value", adcParameters[it["name"].textValue()] as String)
                    }
        }

        if (!template.has("labels")) {
            template.replace("labels", mapper.createObjectNode())
        }

        val labels = template["labels"] as ObjectNode

        val base = apc as AuroraDeploymentConfig
        if (!labels.has("affiliation")) {
            labels.put("affiliation", base.affiliation)
        }

        if (!labels.has("app")) {
            labels.put("app", base.name)
        }

        val result = openShiftClient.post("processedtemplate", namespace = base.namespace, payload = template)

        return result.body["objects"].asSequence().toList()
    }
}