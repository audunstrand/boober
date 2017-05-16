package no.skatteetaten.aurora.boober.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import no.skatteetaten.aurora.boober.controller.security.User
import no.skatteetaten.aurora.boober.controller.security.UserDetailsProvider
import no.skatteetaten.aurora.boober.model.ApplicationId
import no.skatteetaten.aurora.boober.model.AuroraConfig
import no.skatteetaten.aurora.boober.service.openshift.OpenShiftClient
import no.skatteetaten.aurora.boober.service.openshift.OpenShiftResourceClient
import no.skatteetaten.aurora.boober.utils.AuroraConfigHelper
import spock.lang.Specification
import spock.mock.DetachedMockFactory

@SpringBootTest(classes = [
    no.skatteetaten.aurora.boober.Configuration,
    AuroraConfigService,
    GitService,
    OpenShiftClient,
    EncryptionService,
    OpenShiftResourceClient,
    Config
], properties = [
    "boober.git.url=/tmp/boober-test",
    "boober.git.checkoutPath=/tmp/boober",
    "boober.git.username=",
    "boober.git.password="
])
class AuroraConfigServiceTest extends Specification {

  public static final String ENV_NAME = "secrettest"
  public static final String APP_NAME = "verify-ebs-users"
  final ApplicationId aid = new ApplicationId(ENV_NAME, APP_NAME)

  @Configuration
  static class Config {
    private DetachedMockFactory factory = new DetachedMockFactory()

    @Bean
    AuroraDeploymentConfigService auroraDeploymentConfigService() {
      factory.Mock(AuroraDeploymentConfigService)
    }

    @Bean
    UserDetailsProvider userDetailsProvider() {
      factory.Mock(UserDetailsProvider)
    }
  }

  @Autowired
  AuroraConfigService service

  @Autowired
  UserDetailsProvider userDetailsProvider

  @Autowired
  GitService gitService

  private void createRepoAndSaveFiles(String affiliation, AuroraConfig auroraConfig) {
    GitServiceHelperKt.createInitRepo(affiliation)
    userDetailsProvider.authenticatedUser >> new User("test", "", "Test Foo")
    service.save(affiliation, auroraConfig)
  }

  def "Should successfully save AuroraConfig and secrets to git"() {
    given:
      GitServiceHelperKt.createInitRepo("aos")
      def auroraConfig = AuroraConfigHelper.createAuroraConfig(aid, ["/tmp/foo/latest.properties": "FOO=BAR"])
      userDetailsProvider.authenticatedUser >> new User("foobar", "", "Foo Bar")

    when:
      service.save("aos", auroraConfig)
      def git = gitService.checkoutRepoForAffiliation("aos")
      def gitLog = git.log().call().head()
      gitService.closeRepository(git)

    then:
      gitLog.authorIdent.name == "Foo Bar"
      gitLog.fullMessage == "Added: 5, Modified: 0, Deleted: 0"
  }

  def "Should patch AuroraConfigFile and push changes to git"() {
    given:
      def auroraConfig = AuroraConfigHelper.createAuroraConfig(aid, ["/tmp/foo/latest.properties": "FOO=BAR"])
      createRepoAndSaveFiles("aos", auroraConfig)
      def jsonOp = """[{
  "op": "replace",
  "path": "/version",
  "value": "3"
}]
"""

    when:
      def filename = "${aid.environmentName}/${aid.applicationName}.json"
      def jsonNode = service.patchAuroraConfigFile(filename, auroraConfig, jsonOp)
      service.updateAuroraConfigFile("aos", filename, jsonNode)
      def git = gitService.checkoutRepoForAffiliation("aos")
      def gitLog = git.log().call().head()
      gitService.closeRepository(git)

    then:
      jsonNode.has("version")
      jsonNode.get("version").asText() == "3"
      gitLog.fullMessage == "Added: 0, Modified: 1, Deleted: 0"
  }

}
