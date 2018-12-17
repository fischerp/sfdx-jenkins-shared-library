#!/usr/bin/env groovy
import com.claimvantage.sjsl.Help
import com.claimvantage.sjsl.Org
import com.claimvantage.sjsl.Package

def call(Map parameters = [:]) {
    
    String glob = parameters.glob ?: 'config/project-scratch-def.*.json'
    Help help = parameters.help ?: null
    Package[] packages = parameters.packages ?: []
    Closure beforeTest = parameters.beforeTest ?: null
    
    pipeline {
        node {
            if (help) {
                stage("help") {
                    processHelp(help: help)
                }
            }
            stage("checkout") {
                checkout(scm: scm, quiet: true)
                retrieveExternals()
            }
            // Use multiple scratch orgs in parallel
            withOrgsInParallel(glob: glob) { org ->
                stage("${org.name} create") {
                    createScratchOrg org
                }
                if (packages.size() > 0) {
                    stage("${org.name} install") {
                        for (def p in packages) {
                            installPackage(org: org, package: p)
                        }
                    }
                }
                stage("${org.name} push") {
                    pushToOrg org
                }
                if (beforeTest) {
                    stage("${org.name} before test") {
                        beforeTest org
                    }
                }
                stage("${org.name} test") {
                    runApexTests org
                }
                stage("${org.name} delete") {
                    deleteScratchOrg org
                }
            }
            stage("publish") {
                junit keepLongStdio: true, testResults: 'tests/**/*-junit.xml'
            }
            stage("clean") {
                // Always remove workspace and don't fail the build for any errors
                cleanWs notFailBuild: true
            }
        }
    }
}
