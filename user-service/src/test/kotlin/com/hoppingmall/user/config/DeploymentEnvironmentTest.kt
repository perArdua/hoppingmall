package com.hoppingmall.user.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.yaml.snakeyaml.Yaml
import java.io.File

@DisplayName("배포 설정의 user-service 환경 변수")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DeploymentEnvironmentTest {

    @ParameterizedTest
    @ValueSource(strings = [COMPOSE, K8S, K8S_CD])
    fun 게이트웨이_뒤의_user_service는_게이트웨이가_덧붙인_주소를_클라이언트_IP로_쓴다(file: String) {
        assertThat(userServiceEnvironment(file)).containsEntry("USER_RATELIMIT_TRUSTEDPROXYCOUNT", "1")
    }

    private fun userServiceEnvironment(file: String): Map<String, String?> {
        val text = repositoryRoot().resolve(file).readText()
        return if (file == COMPOSE) composeEnvironment(text) else kubernetesEnvironment(text)
    }

    @Suppress("UNCHECKED_CAST")
    private fun composeEnvironment(text: String): Map<String, String?> {
        val root = Yaml().load<Map<String, Any?>>(text)
        val services = root["services"] as Map<String, Any?>
        val service = services["user-service"] as Map<String, Any?>
        val environment = service["environment"] as Map<String, Any?>
        return environment.mapValues { it.value?.toString() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun kubernetesEnvironment(text: String): Map<String, String?> {
        val workload = Yaml().loadAll(text)
            .map { it as Map<String, Any?> }
            .single { it["kind"] == "Deployment" || it["kind"] == "Rollout" }
        val template = (workload["spec"] as Map<String, Any?>)["template"] as Map<String, Any?>
        val containers = (template["spec"] as Map<String, Any?>)["containers"] as List<Map<String, Any?>>
        val container = containers.single { it["name"] == "user-service" }
        val env = container["env"] as List<Map<String, Any?>>
        return env.associate { it["name"].toString() to it["value"]?.toString() }
    }

    private fun repositoryRoot(): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { it.resolve(COMPOSE).isFile }

    companion object {
        private const val COMPOSE = "docker-compose.yml"
        private const val K8S = "k8s/services/user-service.yml"
        private const val K8S_CD = "k8s-cd/base/user-service/rollout.yaml"
    }
}
