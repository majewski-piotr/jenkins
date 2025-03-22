pipelineJob('apply_all_infra') {
    description('job that builds all infrastructure')
    definition {
        cps {
            script("""
                pipeline {
                    agent any
                    stages {
                        stage('Build network') {
                            steps {
                                build job: 'terraform-job',
                                      parameters: [
                                          string(name: 'GIT_URL', value: 'https://github.com/majewski-piotr/aws-infra.git'),
                                          string(name: 'CREDENTIALS_ID', value: 'aws-terraform'),
                                          string(name: 'WORKING_DIR', value: 'network'),
                                          booleanParam(name: 'DESTROY', value: false),
                                          booleanParam(name: 'AUTO_APPROVE', value: true)
                                      ],
                                      wait: true
                            }
                        }
                        stage('Build security') {
                            steps {
                                build job: 'terraform-job',
                                      parameters: [
                                          string(name: 'GIT_URL', value: 'https://github.com/majewski-piotr/aws-infra.git'),
                                          string(name: 'CREDENTIALS_ID', value: 'aws-terraform'),
                                          string(name: 'WORKING_DIR', value: 'security'),
                                          booleanParam(name: 'DESTROY', value: false),
                                          booleanParam(name: 'AUTO_APPROVE', value: true)
                                      ],
                                      wait: true
                            }
                        }
                        stage('Test network') {
                            steps {
                                build job: 'terraform-job',
                                      parameters: [
                                          string(name: 'GIT_URL', value: 'https://github.com/majewski-piotr/aws-infra.git'),
                                          string(name: 'CREDENTIALS_ID', value: 'aws-terraform'),
                                          string(name: 'WORKING_DIR', value: 'access_analyzer'),
                                          booleanParam(name: 'DESTROY', value: false),
                                          booleanParam(name: 'AUTO_APPROVE', value: true)
                                      ],
                                      wait: true
                            }
                        }
                        stage('Build computing') {
                            steps {
                                build job: 'terraform-job',
                                      parameters: [
                                          string(name: 'GIT_URL', value: 'https://github.com/majewski-piotr/aws-infra.git'),
                                          string(name: 'CREDENTIALS_ID', value: 'aws-terraform'),
                                          string(name: 'WORKING_DIR', value: 'compute'),
                                          booleanParam(name: 'DESTROY', value: false),
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
