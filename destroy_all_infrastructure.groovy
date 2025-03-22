pipelineJob('destroy_all_infra') {
    description('job that destroys all infrastructure')
    definition {
        cps {
            script("""
                pipeline {
                    agent any
                    stages {
                        stage('Destroy computing') {
                            steps {
                                build job: 'terraform-job',
                                      parameters: [
                                          string(name: 'GIT_URL', value: 'https://github.com/majewski-piotr/aws-infra.git'),
                                          string(name: 'CREDENTIALS_ID', value: 'aws-terraform'),
                                          string(name: 'WORKING_DIR', value: 'compute'),
                                          booleanParam(name: 'DESTROY', value: true),
                                          booleanParam(name: 'AUTO_APPROVE', value: true)
                                      ],
                                      wait: true
                            }
                        }
                        stage('Destroy network tests') {
                            steps {
                                build job: 'terraform-job',
                                      parameters: [
                                          string(name: 'GIT_URL', value: 'https://github.com/majewski-piotr/aws-infra.git'),
                                          string(name: 'CREDENTIALS_ID', value: 'aws-terraform'),
                                          string(name: 'WORKING_DIR', value: 'access_analyzer'),
                                          booleanParam(name: 'DESTROY', value: true),
                                          booleanParam(name: 'AUTO_APPROVE', value: true)
                                      ],
                                      wait: true
                            }
                        }
                        stage('Destroy security') {
                            steps {
                                build job: 'terraform-job',
                                      parameters: [
                                          string(name: 'GIT_URL', value: 'https://github.com/majewski-piotr/aws-infra.git'),
                                          string(name: 'CREDENTIALS_ID', value: 'aws-terraform'),
                                          string(name: 'WORKING_DIR', value: 'security'),
                                          booleanParam(name: 'DESTROY', value: true),
                                          booleanParam(name: 'AUTO_APPROVE', value: true)
                                      ],
                                      wait: true
                            }
                        }
                        stage('Destroy network') {
                            steps {
                                build job: 'terraform-job',
                                      parameters: [
                                          string(name: 'GIT_URL', value: 'https://github.com/majewski-piotr/aws-infra.git'),
                                          string(name: 'CREDENTIALS_ID', value: 'aws-terraform'),
                                          string(name: 'WORKING_DIR', value: 'network'),
                                          booleanParam(name: 'DESTROY', value: true),
                                          booleanParam(name: 'AUTO_APPROVE', value: true)
                                      ],
                                      wait: true
                            }
                        }
                    }
                }
            """)
        }
    }
}
