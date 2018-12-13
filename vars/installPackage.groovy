import com.claimvantage.jsl.Org

// Name is just for info purposes
def call(Org org, name, versionId, password) {

    echo "Install ${name} package in or ${org.name}"

    shWithStatus "sfdx force:package:install --targetusername ${org.username} --package ${versionId} --installationkey ${password} --wait 15 --noprompt"
}
