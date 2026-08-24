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
        string(description: 'Branch to use', name: 'branch', defaultValue: 'main')
        choice(choices: moduleString, description: 'Module', name: 'module')
    }

    stages {
        stage("Checkout") {
            steps {
               dir("${params.module}") {
                    git credentialsId: 'github-bot-ssh', url: "git@github.com:microprofile/${params.module}.git", branch: ${params.releaseVersion}
                }
            }
        }
        stage("Promote Main Artifacts") {
            steps {
                dir("${params.module}") {
                    withCredentials([file(credentialsId: 'secret-subkeys.asc', variable: 'KEYRING')]) {
                        sh 'gpg --batch --import "${KEYRING}"'
                        sh 'for fpr in $(gpg --list-keys --with-colons  | awk -F: \'/fpr:/ {print $10}\' | sort -u); do echo -e "5\ny\n" |  gpg --batch --command-fd 0 --expert --edit-key ${fpr} trust; done'
                    }
                    sshagent(['github-bot-ssh']) {
                        sh '''
                            git config --global user.email "microprofile-bot@eclipse.org"
                            git config --global user.name "Eclipse MicroProfile bot"
                        '''
                    sh "mvn --batch-mode -s /home/jenkins/.m2/settings.xml -Ppromote-stage -Drelease -Dnexus.staging.repository=${params.module}-maven2-staging -DskipTests deploy"
                    }
                }
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
