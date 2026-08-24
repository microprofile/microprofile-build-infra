def modules = ['microprofile','microprofile-bom','microprofile-config','microprofile-context-propagation','microprofile-fault-tolerance',
	'microprofile-health','microprofile-jwt-auth','microprofile-metrics',
	'microprofile-open-api','microprofile-opentracing','microprofile-parent', 'microprofile-rest-client',
	'microprofile-reactive-streams-operators', 'microprofile-reactive-messaging', 
    'microprofile-lra', 'microprofile-graphql', 'microprofile-telemetry','microprofile-jwt-bridge']
def moduleString = modules.join('\n')
pipeline {
    agent any
    tools {
        maven 'apache-maven-latest'
        jdk 'temurin-jdk17-latest'
    }
    parameters {
        string(description: 'The release version', name: 'releaseVersion')
        choice(choices: moduleString, description: 'Module', name: 'module')
    }

    stages {
        stage("Checkout") {
            steps {
               dir("${params.module}") {
                    git credentialsId: 'github-bot-ssh', url: "git@github.com:microprofile/${params.module}.git", branch: params.branch
                }
            }
        }
        stage("Promote Main Artifacts") {
            steps {
                sh "mvn --batch-mode -s /home/jenkins/.m2/settings.xml -Ppromote-stage -Drelease -Dnexus.staging.repository=${params.module}-maven2-staging -DskipTests deploy"
            }
        }
        stage("Move Specs From Staging") {
            when {
                expression { params.module != "microprofile-parent" }
            }
            steps {
                sshagent ( ['projects-storage.eclipse.org-bot-ssh']) {
                    sh "ssh genie.microprofile@projects-storage.eclipse.org [ -e /home/data/httpd/download.eclipse.org/microprofile/staging/${params.module}-${params.releaseVersion} ] || (echo 'The requested module ${params.module}-${params.releaseVersion} not found in microprofile/staging/ directory' && exit 1)"
                    sh "ssh genie.microprofile@projects-storage.eclipse.org mv /home/data/httpd/download.eclipse.org/microprofile/staging/${params.module}-${params.releaseVersion} /home/data/httpd/download.eclipse.org/microprofile/"
                }
            }
        }
    }
}
