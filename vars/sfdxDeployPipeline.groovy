#!/usr/bin/env groovy

def call(Map parameters = [:]) {
    def packagesToInstall = parameters.packagesToInstall ?: []

    pipeline {
        node {
            // We want to set some properties, such as parameters
            // properties(
            //     propertiesConfigured
            // )
            
            // We don't want the same deployment to run multiple times at same time
            // We also want to make sure we don't starve the job queue (limiting job to run up to a certain time)
            throttle([]) {
                timeout(time: 4, unit: 'HOURS') {
                    stage("Checkout") {
                        checkout(scm: scm)
                    }
                    stage("Install packages") {
                        if (packagesToInstall) {
                            def installedPackages = retrieveInstalledPackages()

                            for (p in packagesToInstall) {
                                if (shouldInstallPackage(packageVersionId: p, installedPackages: installedPackages)) {
                                    echo "Yes"
                                } else {
                                    echo "No"
                                }
                            }
                        } else {
                            echo "No packages to install."
                        }
                    }

                    stage("Install unmanaged code") {
                    }
                }
            }
        }
    }
}

def shouldInstallPackage(Map parameters = [:]) {
    def packageVersionId = parameters.packageVersionId
    def versionPossibleToInstall = retrievePackageVersionString(packageVersionId)
    def packageDefinition = retrievePackage(packageVersionId)
    def packageNamespace = packageDefinition.NamespacePrefix
    def packageName = packageDefinition.Name

    def installedPackages = parameters.installedPackages
    def installedVersion = installedPackages[packageNamespace]

    def result = versionPossibleToInstall > installedVersion
    echo """
        Name: ${packageName}, Namespace ${packageNamespace} \n
        Installed Version ${installedVersion} > Version to Install ${versionPossibleToInstall}? ${result}
    """

    return result
}

def retrievePackageVersionString(packageVersionId) {
    def v = retrievePackageVersion(packageVersionId)
    String result = "${v.MajorVersion}.${v.MinorVersion}.${v.PatchVersion}.${v.BuildNumber}"
    return result
}

def retrievePackageVersion(packageVersionId) {
    packageVersionId = retrieveSfdxAlias packageVersionId
    def subscriberPackageVersion = shWithResult(
        """ \
        sfdx force:data:soql:query \
        --json \
        --usetoolingapi \
        --query " \
            SELECT Name, MajorVersion, MinorVersion, PatchVersion, BuildNumber, SubscriberPackageId
            FROM SubscriberPackageVersion
            WHERE Id = '${packageVersionId}'\"
        """)
    return subscriberPackageVersion.records[0]
}

// force:package:install accepts alias to install it, however currently there is no native way to get the package ID
def retrieveSfdxAlias(versionId) {
    def sfdxProject = 'sfdx-project.json'
    if (fileExists("${sfdxProject}")) {
        def data = readJSON(file:"${sfdxProject}")
        def isVersionIdAliasSet = data['packageAliases'] != null && data['packageAliases']["${versionId}"]
        return isVersionIdAliasSet ? data['packageAliases']["${versionId}"] : versionId
    }
    return versionId
}

def retrievePackage(packageVersionId) {
    def p = retrievePackageVersion(packageVersionId)
    def subscriberPackage = shWithResult """ \
        sfdx force:data:soql:query \
        --json \
        --usetoolingapi \
        --query " \
            SELECT Name, NamespacePrefix
            FROM SubscriberPackage 
            WHERE Id = '${p.SubscriberPackageId}'\"
    """
    return subscriberPackage.records[0]
}

def retrieveInstalledPackages() {
    // Using Tooling API instead of sfdx force:package:installed:list due to Salesforce query timeout issues
    def installedPackagesResults = shWithResult """ \
        sfdx force:data:soql:query \
        --json \
        --usetoolingapi \
        --query="
            SELECT
                Id,
                SubscriberPackage.NamespacePrefix,
                SubscriberPackage.Name,
                SubscriberPackageVersionId
                FROM InstalledSubscriberPackage"
    """

    def installedPackages = [:]
    for (installedSubscriberPackage in installedPackagesResults.records) {
        def packageVersionId = installedSubscriberPackage.SubscriberPackageVersionId
        def packageVersionString = retrievePackageVersionString(packageVersionId)
        def packageNamespace = installedSubscriberPackage.SubscriberPackage.NamespacePrefix
        installedPackages[packageNamespace] = packageVersionString
    }
    
    return installedPackages
}