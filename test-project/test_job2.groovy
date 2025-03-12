job('project1/job2') {
    description('A simple example job 2')
    triggers {
        scm('H/15 * * * *')
    }
    steps {
        shell('echo Hello, Jenkins!')
    }
}
