package com.factstore.application

import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.command.CreateTrailCommand

enum class CiProvider { GITHUB_ACTIONS, GITLAB_CI, JENKINS, CIRCLECI, AZURE_DEVOPS }

object CiContextResolver {

    fun resolve(providerHeader: String?): CiProvider? = when (providerHeader?.lowercase()?.trim()) {
        "github-actions", "github_actions" -> CiProvider.GITHUB_ACTIONS
        "gitlab-ci", "gitlab_ci" -> CiProvider.GITLAB_CI
        "jenkins" -> CiProvider.JENKINS
        "circleci", "circle-ci" -> CiProvider.CIRCLECI
        "azure-devops", "azure_devops" -> CiProvider.AZURE_DEVOPS
        else -> null
    }

    fun getCommitSha(provider: CiProvider, env: Map<String, String> = System.getenv()): String? = when (provider) {
        CiProvider.GITHUB_ACTIONS -> env["GITHUB_SHA"]
        CiProvider.GITLAB_CI -> env["CI_COMMIT_SHA"]
        CiProvider.JENKINS -> env["GIT_COMMIT"]
        CiProvider.CIRCLECI -> env["CIRCLE_SHA1"]
        CiProvider.AZURE_DEVOPS -> env["BUILD_SOURCEVERSION"]
    }

    fun getBranch(provider: CiProvider, env: Map<String, String> = System.getenv()): String? = when (provider) {
        CiProvider.GITHUB_ACTIONS -> env["GITHUB_REF_NAME"]
        CiProvider.GITLAB_CI -> env["CI_COMMIT_REF_NAME"]
        CiProvider.JENKINS -> env["GIT_BRANCH"]
        CiProvider.CIRCLECI -> env["CIRCLE_BRANCH"]
        CiProvider.AZURE_DEVOPS -> env["BUILD_SOURCEBRANCH"]
    }

    fun getBuildUrl(provider: CiProvider, env: Map<String, String> = System.getenv()): String? = when (provider) {
        CiProvider.GITHUB_ACTIONS -> {
            val server = env["GITHUB_SERVER_URL"] ?: return null
            val repo = env["GITHUB_REPOSITORY"] ?: return null
            val runId = env["GITHUB_RUN_ID"] ?: return null
            "$server/$repo/actions/runs/$runId"
        }
        CiProvider.GITLAB_CI -> env["CI_JOB_URL"]
        CiProvider.JENKINS -> env["BUILD_URL"]
        CiProvider.CIRCLECI -> env["CIRCLE_BUILD_URL"]
        CiProvider.AZURE_DEVOPS -> {
            val collection = env["SYSTEM_TEAMFOUNDATIONCOLLECTIONURI"] ?: return null
            val project = env["SYSTEM_TEAMPROJECT"] ?: return null
            val buildId = env["BUILD_BUILDID"] ?: return null
            "$collection$project/_build/results?buildId=$buildId"
        }
    }

    fun enrich(
        request: CreateTrailRequest,
        provider: CiProvider,
        env: Map<String, String> = System.getenv()
    ): CreateTrailRequest = request.copy(
        gitCommitSha = request.gitCommitSha ?: getCommitSha(provider, env),
        gitBranch = request.gitBranch ?: getBranch(provider, env),
        buildUrl = request.buildUrl ?: getBuildUrl(provider, env)
    )

    fun enrich(
        command: CreateTrailCommand,
        provider: CiProvider,
        env: Map<String, String> = System.getenv()
    ): CreateTrailCommand = command.copy(
        gitCommitSha = command.gitCommitSha ?: getCommitSha(provider, env),
        gitBranch = command.gitBranch ?: getBranch(provider, env),
        buildUrl = command.buildUrl ?: getBuildUrl(provider, env)
    )
}
