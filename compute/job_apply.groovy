pipelineJob('compute/apply') {
    description('job that builds computing layer of aws infrastructure')
    definition {
        cps {
            script("""
                pipeline {
                    agent any
                    stages {
                        stage('Trigger Generic Terraform Apply') {
                            steps {
                                // Trigger the generic job with pre-defined parameters.
                                build job: 'terraform-job',
                                      parameters: [
                                          string(name: 'GIT_URL', value: 'https://github.com/majewski-piotr/aws-infra.git'),
                                          string(name: 'CREDENTIALS_ID', value: 'aws-terraform'),
                                          string(name: 'WORKING_DIR', value: 'compute'),
                                          booleanParam(name: 'DESTROY', value: false)
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
