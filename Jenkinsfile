def label = "worker-${UUID.randomUUID().toString()}"
    
podTemplate(label: label,
 containers: [
  //containerTemplate(name: 'git', image: 'alpine/git', ttyEnabled: true, command: 'cat'),
  //containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-8-alpine', command: 'cat', ttyEnabled: true),
  //containerTemplate(name: 'gradle', image: 'gradle:4.5.1-jdk9', command: 'cat', ttyEnabled: true),
  containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true),
  containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:v1.8.8', command: 'cat', ttyEnabled: true),
  containerTemplate(name: 'helm', image: 'lachlanevenson/k8s-helm:latest', command: 'cat', ttyEnabled: true),
  containerTemplate(name: 'jnlp', image: 'jenkinsci/jnlp-slave:latest', args: '${computer.jnlpmac} ${computer.name}')
],
volumes: [
  //hostPathVolume(mountPath: '/home/gradle/.gradle', hostPath: '/tmp/jenkins/.gradle'),
  //secretVolume(secretName: 'docker-config', mountPath: '/tmp'),
  hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')
]) {
  node(label) {
    def myRepo = checkout scm
    def gitCommit = myRepo.GIT_COMMIT
    def gitBranch = myRepo.GIT_BRANCH
    def shortGitCommit = "${gitCommit[0..10]}"
    def previousGitCommit = sh(script: "git rev-parse ${gitCommit}~", returnStdout: true)
    def K8S_DEPLOYMENT_NAME = 'mydevops-app'
    
 
    stage('Check running containers') {
    
    if (gitBranch == 'master') {
            echo 'I only execute on the master branch'
        
            container('docker') {
                // example to show you can run docker commands when you mount the socket
                sh 'hostname'
                sh 'hostname -i'
                sh 'docker ps'
                sh 'chmod +x gradlew'
                
            }
            } else {
            echo 'I execute elsewhere'
        }
    }
    stage('Build') {
      //container('gradle') {
      sh 'chmod +x gradlew'
      // sh 'gradle test'
        sh './gradlew clean build'
     // }
    }
    stage('Create Docker images') {
      container('docker') {
        withCredentials([[$class: 'UsernamePasswordMultiBinding',
          credentialsId: 'bsldockerhub',
          usernameVariable: 'DOCKER_HUB_USER',
          passwordVariable: 'DOCKER_HUB_PASSWORD']]) {
          sh """
            docker login -u ${DOCKER_HUB_USER} -p ${DOCKER_HUB_PASSWORD}
            docker build -t apidevops/myimage:${gitCommit} .
            docker push apidevops/myimage:${gitCommit}
            """
        }
      }
    }
    
     stage('Deploy New Build To Kubernetes') {
    	container('kubectl') {
               
               // below comand to run kubectl using a config file present in /deployment/test folder
              //    sh("kubectl apply -f ./deployment/test/service.yaml")
                //    sh("kubectl create configmap mydevopsapi-config --from-file=./deployment/test/")
                sh("kubectl create configmap mydevopsapi-config --from-literal=COMMIT_ID=${gitCommit} -o yaml --dry-run | kubectl replace --force  -f -")
                sh("sed -i s/IMAGE_TAG/${gitCommit}/ ./deployment/test/service.yaml")
                sh("kubectl apply -f ./deployment/test/")
                }
            }
            
    stage('Run kubectl') {
      container('kubectl') {
        sh "kubectl get pods"
      }
    }
    stage('Run helm') {
      container('helm') {
        sh "helm list"
      }
    }
  }
}
