Closure generateRun(String branchName, String gitUrl, String command, String folder) {
    return {
        dir(folder) {
            stage("Checkout ${folder}") {
                git branch: "$branchName", url: "$gitUrl"
            }
            stage("Run Tests ${folder}") {
                try {
                    withMaven(maven: 'maven 3.9.6') {
                        status = sh(script: "$command", returnStatus: true)
                    }
                } catch (e) {
                    currentBuild.status = "UNSTABLE"
                } finally {
                    stash includes: 'target/allure-results/*', name: folder
                }
            }
        }
    }
}

node('maven') {
    timestamps {
        wrap([$class: 'BuildUser']) {
            currentBuild.description = "User: ${env.BUILD_USER}"
        }
        def params = readYaml text: env.YAML_CONFIG ?: [:]
        params.each {
            k, v -> env.setProperty(k, v)
        }

        def runs = [:]

        if (env.API.toBoolean()) {
            runs['API'] = generateRun("homework_3", "https://github.com/andriuha1987/OTUSLearning.git", "mvn test", "API")
        }
        if (env.STUB.toBoolean()) {
            runs['STUB'] = generateRun("homework_5", "https://github.com/andriuha1987/OTUSLearning.git", "mvn test", "STUB")
        }
        if (env.UI.toBoolean()) {
            mvnCommand = "mvn test -Dbrowser=${env.BROWSER_NAME} -Dwebdriver.base.url=${env.BASE_URL} -Dbrowser.version=${env.BROWSER_VERSION} -Dselenoid.url=${env.SELENOID_URL} -Dselenoid.enabled=${env.SELENOID_ENABLED}"
            runs['UI'] = generateRun("homework_4", "https://github.com/andriuha1987/OTUSLearning.git", mvnCommand, "UI")
        }
        if (env.MOBILE.toBoolean()) {
            runs['MOBILE'] = generateRun("homework", "https://github.com/andriuha1987/OtusAppium.git", "mvn test", "MOBILE")
        }

        parallel runs

        stage("Unstash allure reports") {
            runs.each {
                try {
                    unstash it.key
                } catch (ignored) {
                }
            }
        }

        stage("Allure report") {
            allure includeProperties: false, results: [[path: 'target/allure-results']]
        }

        // на будущее прочитать про секьюрность для httpRequest https://community.jenkins.io/t/httprequest-with-two-credentials-how-to-avoid-insecure-interpolation-of-sensitive-variables/6282/2
        stage("Send to Telegram") {
            summary = junit testResults: "**/target/surefire-reports/*.xml", skipPublishingChecks: true
            resultText = "RESULTS - Total: ${summary.totalCount} Passed: ${summary.passCount} Failed: ${summary.failCount} Skipped: ${summary.skipCount}"
            allureReportUrl = "${env.BUILD_URL.replace('localhost', '127.0.0.1')}allure/"
            withCredentials([string(credentialsId: 'CHAT_ID', variable: 'CHAT_ID'), string(credentialsId: 'TOKEN_BOT', variable: 'TOKEN_BOT')]) {
                httpRequest httpMode: 'POST',
                        requestBody: """{\"chat_id\": ${CHAT_ID}, \"text\": \"AUTOTESTS RUNNING FINISHED\n$resultText\nAllure report - $allureReportUrl\"}""",
                        contentType: 'APPLICATION_JSON',
                        url: "https://api.telegram.org/bot${TOKEN_BOT}/sendMessage",
                        validResponseCodes: '200'
            }
        }
    }
}