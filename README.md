# mydevopsapi-v1

https://cloudcompilerr.wordpress.com/2018/06/03/docker-jenkins-kubernetes-run-jenkins-on-kubernetes-cluster/

Lets outline the aim of this activity / process-

We aim to have a docker file for creating an image of our spring boot application
 Start Minikube ( Kubernetes with single cluster)
 Deploy Jenkins as POD ( container ) on minikube using a kubernets configuration file ( and helm)
 Now, use this Jenkins for CI/CD
 Open Jenkins UI – point SCM to your git hub repo – Select pipeline style job and follow the CI/CD steps using the scripted Jenkinsfile present in the git hub repo
Used Jenkinsfile is very important – It contains podTemplates for creating Jenkins Slaves , Docker, Gradle and other pods and also perform below steps –
Build the application, create docker image using dockerFile, push image to docker hub
Now CD from Jenkins to Kubernetes i.e pulling this image and creating pod on minikube will be discussed in future blogs
 

Steps In Details-

 

Dockerfile used –
FROM frolvlad/alpine-oraclejdk8:slim

EXPOSE 9090

RUN mkdir -p /app/

ADD build/libs/mydevopsapi-v1-0.0.1-SNAPSHOT.jar /app/mydevopsapi-v1.jar

ENTRYPOINT [“java”, “-jar”, “/app/mydevopsapi-v1.jar”]

 Jenkinsfile used –
def label = “worker-${UUID.randomUUID().toString()}”

podTemplate(label: label,

containers: [

  //containerTemplate(name: ‘git‘, image: ‘alpine/git‘, ttyEnabled: true, command: ‘cat’),

  //containerTemplate(name: ‘maven‘, image: ‘maven:3.3.9-jdk-8-alpine’, command: ‘cat’, ttyEnabled: true),

  //containerTemplate(name: ‘gradle‘, image: ‘gradle:4.5.1-jdk9′, command: ‘cat’, ttyEnabled: true),

  containerTemplate(name: ‘docker’, image: ‘docker’, command: ‘cat’, ttyEnabled: true),

  containerTemplate(name: ‘kubectl‘, image: ‘lachlanevenson/k8s-kubectl:v1.8.8′, command: ‘cat’, ttyEnabled: true),

  containerTemplate(name: ‘helm’, image: ‘lachlanevenson/k8s-helm:latest’, command: ‘cat’, ttyEnabled: true),

  containerTemplate(name: ‘jnlp‘, image: ‘jenkinsci/jnlp-slave:latest’, args: ‘${computer.jnlpmac} ${computer.name}’)

],

volumes: [

  //hostPathVolume(mountPath: ‘/home/gradle/.gradle‘, hostPath: ‘/tmp/jenkins/.gradle‘),

  hostPathVolume(mountPath: ‘/var/run/docker.sock’, hostPath: ‘/var/run/docker.sock’)

]) {

  node(label) {

    def myRepo = checkout scm

    def gitCommit = myRepo.GIT_COMMIT

    def gitBranch = myRepo.GIT_BRANCH

    def shortGitCommit = “${gitCommit[0..10]}”

    def previousGitCommit = sh(script: “git rev-parse ${gitCommit}~”, returnStdout: true)

    

    stage(‘Check running containers’) {

            container(‘docker’) {

                // example to show you can run docker commands when you mount the socket

                sh ‘hostname‘

                sh ‘hostname -i’

                sh ‘docker ps‘

                sh ‘chmod +x gradlew‘

                

            }

        }

    stage(‘Build’) {

      //container(‘gradle‘) {

      sh ‘chmod +x gradlew‘

      // sh ‘gradle test’

        sh ‘./gradlew clean build’

     // }

    }

    stage(‘Create Docker images’) {

      container(‘docker’) {

        withCredentials([[$class: ‘UsernamePasswordMultiBinding’,

          credentialsId: ‘dockerhub‘,

          usernameVariable: ‘DOCKER_HUB_USER’,

          passwordVariable: ‘DOCKER_HUB_PASSWORD’]]) {

          sh “””

            docker login -u ${DOCKER_HUB_USER} -p ${DOCKER_HUB_PASSWORD}

            docker build -t smanish3007/myimage:${gitCommit} .

            docker push smanish3007/myimage:${gitCommit}

            “””

        }

      }

    }

    stage(‘Run kubectl‘) {

      container(‘kubectl‘) {

        sh “kubectl get pods”

      }

    }

    stage(‘Run helm’) {

      container(‘helm’) {

        sh “helm list”

      }

    }

  }

}

 Reference URLs-
https://itnext.io/deploy-jenkins-with-dynamic-slaves-in-minikube-8aef5404e9c1
https://akomljen.com/set-up-a-jenkins-ci-cd-pipeline-with-kubernetes
 

 

Follow below steps for helm and jenkins set up on minikube cluster –
 Install  kubernetes and helm-
$ brew install kubectl
brew install kubernetes-helm
Have a jenkins Volume persistence configuration file . In my case, I have jenkins-volume.yaml with content as – apiVersion: v1
kind: PersistentVolume
metadata:
name: jenkins-pv
namespace: jenkins-project
spec:
storageClassName: jenkins-pv
accessModes:
– ReadWriteOnce
capacity:
storage: 20Gi
persistentVolumeReclaimPolicy: Retain
hostPath:
path: /data/jenkins-volume/
 Run command –
kubectl create -f jenkins-volume.yaml
 Use Helm to install Jenkins
Start with initializing your current directory with helm:

We will deploy a Jenkins master-slave cluster utilizing the Jenkins Kubernetes plugin. Here you can find the official chart.

We use the jenkins-config.yaml as template to provide our own values which are necessary for our setup. We will claim our volume and mount the Docker socket so we can execute Docker commands inside our slave pods.

Because we are using minikube we need to use NodePort as service type. Only cloud providers offer load balancers. We define port 32000 as port.

We can also define which plugins we want to install in our Jenkins. We use some default plugins like git and the pipeline plugin, but we also add the greenballs plugin which will show a green ball instead of a blue ball after a successful build.

Have a jenkins configuration file using which we can create jenkins pod on minikube /kubernetes. In my case, I have a file named jenkins-config.yaml with content as . –# Default values for jenkins.
# This is a YAML-formatted file.
# Declare name/value pairs to be passed into your templates.
# name: value
## Overrides for generated resource names
# See templates/_helpers.tpl
# nameOverride:
# fullnameOverride:

Master:
Name: jenkins-master
Image: “jenkins/jenkins”
ImageTag: “2.109”
ImagePullPolicy: “Always”
Component: “jenkins-master”
UseSecurity: true
AdminUser: admin
# AdminPassword: <defaults to random>
Cpu: “200m”
Memory: “256Mi”
ServicePort: 8080
# For minikube, set this to NodePort, elsewhere use LoadBalancer
# <to set explicitly, choose port between 30000-32767>
ServiceType: NodePort
NodePort: 32000
ServiceAnnotations: {}
ContainerPort: 8080
# Enable Kubernetes Liveness and Readiness Probes
HealthProbes: true
HealthProbesTimeout: 60
SlaveListenerPort: 50000
LoadBalancerSourceRanges:
– 0.0.0.0/0
# List of plugins to be install during Jenkins master start
InstallPlugins:
– kubernetes:1.1
– workflow-aggregator:2.5
– workflow-job:2.17
– credentials-binding:1.13
– git:3.6.4
– greenballs:1.15
# Used to approve a list of groovy functions in pipelines used the script-security plugin. Can be viewed under /scriptApproval
ScriptApproval:
– “method groovy.json.JsonSlurperClassic parseText java.lang.String”
– “new groovy.json.JsonSlurperClassic”
– “staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods leftShift java.util.Map java.util.Map”
– “staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods split java.lang.String”
CustomConfigMap: false
NodeSelector: {}
Tolerations: {}

Agent:
Enabled: true
Image: jenkins/jnlp-slave
ImageTag: 3.10-1
Component: “jenkins-slave”
Privileged: false
Cpu: “200m”
Memory: “256Mi”
# You may want to change this to true while testing a new image
AlwaysPullImage: false
# You can define the volumes that you want to mount for this container
# Allowed types are: ConfigMap, EmptyDir, HostPath, Nfs, Pod, Secret
volumes:
– type: HostPath
hostPath: /var/run/docker.sock
mountPath: /var/run/docker.sock
NodeSelector: {}

Persistence:
Enabled: true
## A manually managed Persistent Volume and Claim
## Requires Persistence.Enabled: true
## If defined, PVC must be created manually before volume will be bound
# ExistingClaim:
## jenkins data Persistent Volume Storage Class
StorageClass: jenkins-pv

Annotations: {}
AccessMode: ReadWriteOnce
Size: 20Gi
volumes:
# – name: nothing
# emptyDir: {}
mounts:
# – mountPath: /var/nothing
# name: nothing
# readOnly: true

NetworkPolicy:
# Enable creation of NetworkPolicy resources.
Enabled: false
# For Kubernetes v1.4, v1.5 and v1.6, use ‘extensions/v1beta1’
# For Kubernetes v1.7, use ‘networking.k8s.io/v1’
ApiVersion: extensions/v1beta1

Install Default RBAC roles and bindings
rbac:
install: false
serviceAccountName: default
# RBAC api version (currently either v1beta1 or v1alpha1)
apiVersion: v1beta1
# Cluster role reference
roleRef: cluster-admin
Run below command to install jenkins-
helm install -f jenkins-config.yaml –name jenkins –namespace jenkins stable/jenkins
helm init
<br>
Below command is very important-
jenkins namespace is not allowed through default service to create pods </br>
kubectl create clusterrolebinding jenkins --clusterrole=cluster-admin --serviceaccount=jenkins:default
</br>
Get your ‘admin’ user password by running:
printf $(kubectl get secret –namespace jenkins jenkins -o jsonpath=”{.data.jenkins-admin-password}” | base64 –decode);echo
Get the Jenkins URL to visit by running these commands in the same shell:
NOTE: It may take a few minutes for the LoadBalancer IP to be available.
You can watch the status of by running ‘kubectl get svc –namespace jenkins -w jenkins’
Login with the password from step 1 and the username: admin

Use kubectl to see all pods : “kubectl get pods –all-namespaces”
You can also monitor through command “minikube dashboard”
In order to create service account for “jenkins” user-cat > /tmp/serviceaccount.yaml <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
name: jenkins
EOF
 

kubectl create -f /tmp/serviceaccount.yaml

kubectl get serviceAccounts

 Once jenkins pod is created , we can view it as . –
And check also your dashboard (choose the jenkins-project).





Check the volume mount with minikube ssh:

$ minikube ssh
$ ls /data
jenkins-volume
All work for minikube is done. Now we can start using Jenkins.

4. Visit and configure the Jenkins master
We can visit the Jenkins master on http://192.168.99.100:3200.

 

 

Now Use above Jenkins running on minikube and available on http://192.168.99.100:3200. to set CI/CD pipeline using Jenkinsfile method-

We are assuming that our code repo has been pushed to GitHub and it has both dockerfile and Jenkinsfile present.
 Pipline Project –
 Screen Shot 2018-06-03 at 10.57.48 PM
 Now add Build Trigger – Screen Shot 2018-06-03 at 11.09.26 PM.png
  Navigate To Advanced  Project Options – select Pipeline – Definition scroll down as “From SCM: –   Screen Shot 2018-06-03 at 11.11.48 PM.png
Click On Save/ Apply . Its done. Now You can manually Build it .
 Upon Building , The steps mentioned in Jenkinsfile is used –
Explanation Of JenkinsFile-

The first thing you will notice is that this is a scripted pipeline. The declarative pipeline is good in most cases, but unfortunately, it’s not quite ready for Kubernetes. Watch this Using scripted pipeline is not a bad thing, but writing it is more advanced and takes more time.

Let’s break down this Jenkinsfile into several pieces. The first part is the workaround because of the bug in kubernetes plugin:

def label = "worker-${UUID.randomUUID().toString()}"
We defined a variable with random UUID so that pod label is different on each run. I encountered this issue when I tried to update the image in pod template, but it didn’t reflect the changes in the pod. The new Kubernetes plugin version 1.2.1 fixes the issue, but I didn’t have a chance to test it yet.

The next part is a pod template where you can define your container images and volumes, among other things:

podTemplate(label: label, containers: [
  containerTemplate(name: 'gradle', image: 'gradle:4.5.1-jdk9', command: 'cat', ttyEnabled: true),
  containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true),
  containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:v1.8.8', command: 'cat', ttyEnabled: true),
  containerTemplate(name: 'helm', image: 'lachlanevenson/k8s-helm:latest', command: 'cat', ttyEnabled: true)
],
volumes: [
  hostPathVolume(mountPath: '/home/gradle/.gradle', hostPath: '/tmp/jenkins/.gradle'),
  hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')
])
It is worth mentioning that command: 'cat', ttyEnabled: true keeps container running. By default, Jenkins uses JNLP slave agent as an executor. This agent is also the part of the pod. Pods in Kubernetes can run many containers. When Jenkins launches JNLP slave agent all other containers defined in pod template will start. They are all running in the same pod and on the same host. If you run the describe pod command when your slave is running you will see 5 containers in this case. JNLP slave agent plus any extra container you defined in pod template:

⚡  kubectl get po jenkins-slave-qvv8b-zg0pg -o jsonpath="{.status.containerStatuses[*].image}"
docker:latest gradle:4.5.1-jdk9 lachlanevenson/k8s-kubectl:v1.8.8 lachlanevenson/k8s-helm:latest jenkins/jnlp-slave:alpine
The second part of the pod template are the volumes. We define volumes per pod and thus mounting them in every container. The volume /var/run/docker.sock is for Docker container to be able to run docker commands, and volume /home/gradle/.gradle acts as a cache on the underlying host.

In node closure you can checkout code repo and define some variables. Some of them are not even used in above Jenkinsfile, but here they are as examples:

  node(label) {
    def myRepo = checkout scm
    def gitCommit = myRepo.GIT_COMMIT
    def gitBranch = myRepo.GIT_BRANCH
    def shortGitCommit = "${gitCommit[0..10]}"
    def previousGitCommit = sh(script: "git rev-parse ${gitCommit}~", returnStdout: true)
This is the full list of available scm variables that you can use:

GIT_BRANCH
GIT_COMMIT
GIT_LOCAL_BRANCH 
GIT_PREVIOUS_COMMIT
GIT_PREVIOUS_SUCCESSFUL_COMMIT
GIT_URL
So, with all these containers where is my workspace? Workspace is also shared between the containers you defined. Pod describe command will give you the exact location /home/jenkins from workspace-volume (rw). You need to checkout the code repo because the worker is disposable and doesn’t share workspace with the master. If you run pwd command in any container, you will get the same workspace dir /home/jenkins/workspace/<JOB_NAME>_<BRANCH_NAME>-VWH7HI3TT3DZNELHV2FSMYHSLYUK2FXGM432ZR7UPED5ZWXZ6DTA.

Important: Jenkins JNLP slave agent is running with Jenkins user. The Jenkins user UID/GID is 10000, which means that workspace owner is the same user. If you are using root user in other containers to do some work you will not have any problems. But, in above example, I used the official gradle container image which is running with the gradle user. The issue is that this user has UID/GID of 1000. This means that gradle commands will probably fail because of permission issues. To fix it you would need to update gradle container to use 10000 as UID/GID for the gradle user or to use custom JNLP slave agent image. You can define non-default JNLP image in pod template also:

containerTemplate(name: 'jnlp', image: 'customnamespace/jnlp-slave:latest', args: '${computer.jnlpmac} ${computer.name}')
And the last part is to run different stages like you would normally do. The only difference is that you need to specify the container name where stage commands will run:

stage('Run kubectl') {
  container('kubectl') {
    sh "kubectl get pods"
  }
}
Everything within a sh closure is running in the shared workspace. You are specifying different container to run specific commands. Be careful with environment variables. Single quotes vs double quotes and how to access different variables in Groovy. For example when using double quotes:

${var} = Groovy parameter
\$var = Bash parameter
Also, environment variables like GIT_COMMIT or GIT_BRANCH are not available inside the containers, but you can define them like this:

container('gradle') {
  sh """
    echo "GIT_BRANCH=${gitBranch}" >> /etc/environment
    echo "GIT_COMMIT=${gitCommit}" >> /etc/environment
    gradle test
    """
}
In case you need to authenticate docker to DockerHub, create a new username and password credential with dockerhub ID and then use withCredentials pipeline script code to expose username and password as environment variables:

withCredentials([[$class: 'UsernamePasswordMultiBinding',
  credentialsId: 'dockerhub',
  usernameVariable: 'DOCKER_HUB_USER',
  passwordVariable: 'DOCKER_HUB_PASSWORD']]) {
  sh """
    docker login -u ${DOCKER_HUB_USER} -p ${DOCKER_HUB_PASSWORD}
    docker build -t smanish3007/my-image:${gitCommit} .
    docker push smanish3007/my-image:${gitCommit}
    """
}
The only thing left is to add Jenkinsfile to your code repository

 

For DockerHub Credentials , go to Manage Jenkins – Manage Credentials – add Credential  user name password format . and just give it an id as ‘dockerhub'

It may be possible that master node in jenkins is offline or no. of executors is less. Go to Manage Jenkins – Manage Node – Configuration symbol – add no. of executors ton 2/3 whatever you want and refresh

 

 

Now. as a result of triggering build on Jenkins, we will see jenkins slave pod created and  image pushed to docker hub-

Started by user admin
Obtained Jenkinsfile from git https://github.com/singhmanishkumar3007/mydevopsapi-v1.git
Running in Durability level: MAX_SURVIVABILITY
[Pipeline] podTemplate
[Pipeline] {
[Pipeline] node
Still waiting to schedule task
jenkins-slave-05zqj-0vwdh is offline
Agent jenkins-slave-05zqj-0vwdh is provisioned from template Kubernetes Pod Template
Agent specification [Kubernetes Pod Template] (worker-2a3eaa1e-9158-43fa-afe9-9d733c2f1ee2): 
* [docker] docker
* [kubectl] lachlanevenson/k8s-kubectl:v1.8.8
* [helm] lachlanevenson/k8s-helm:latest
* [jnlp] jenkinsci/jnlp-slave:latest

Running on jenkins-slave-05zqj-0vwdh in /home/jenkins/workspace/mydevops-pipeline
[Pipeline] {
[Pipeline] checkout
Cloning the remote Git repository
Cloning repository https://github.com/singhmanishkumar3007/mydevopsapi-v1.git
 > git init /home/jenkins/workspace/mydevops-pipeline # timeout=10
Fetching upstream changes from https://github.com/singhmanishkumar3007/mydevopsapi-v1.git
 > git --version # timeout=10
using GIT_ASKPASS to set credentials 
 > git fetch --tags --progress https://github.com/singhmanishkumar3007/mydevopsapi-v1.git +refs/heads/*:refs/remotes/origin/*
 > git config remote.origin.url https://github.com/singhmanishkumar3007/mydevopsapi-v1.git # timeout=10
 > git config --add remote.origin.fetch +refs/heads/*:refs/remotes/origin/* # timeout=10
 > git config remote.origin.url https://github.com/singhmanishkumar3007/mydevopsapi-v1.git # timeout=10
Fetching upstream changes from https://github.com/singhmanishkumar3007/mydevopsapi-v1.git
using GIT_ASKPASS to set credentials 
 > git fetch --tags --progress https://github.com/singhmanishkumar3007/mydevopsapi-v1.git +refs/heads/*:refs/remotes/origin/*
 > git rev-parse refs/remotes/origin/master^{commit} # timeout=10
 > git rev-parse refs/remotes/origin/origin/master^{commit} # timeout=10
Checking out Revision 43c01b4c6ce4879f293afd25d3356a1cacee2c3e (refs/remotes/origin/master)
 > git config core.sparsecheckout # timeout=10
 > git checkout -f 43c01b4c6ce4879f293afd25d3356a1cacee2c3e
Commit message: "changed docker hub repository name"
First time build. Skipping changelog.
[Pipeline] sh
[mydevops-pipeline] Running shell script
+ git rev-parse 43c01b4c6ce4879f293afd25d3356a1cacee2c3e~
[Pipeline] stage
[Pipeline] { (Check running containers)
[Pipeline] container
[Pipeline] {
[Pipeline] sh
[mydevops-pipeline] Running shell script
+ hostname
jenkins-slave-05zqj-0vwdh
[Pipeline] sh
[mydevops-pipeline] Running shell script
+ hostname -i
172.17.0.6
[Pipeline] sh
[mydevops-pipeline] Running shell script
+ docker ps
CONTAINER ID        IMAGE                        COMMAND                  CREATED             STATUS              PORTS               NAMES
c21c4c6b9f83        2232c0bbbb8c                 "cat"                    52 seconds ago      Up 51 seconds                           k8s_docker_jenkins-slave-05zqj-0vwdh_jenkins_50b0c7ff-6756-11e8-ac97-080027a04784_0
8501a68478aa        ead2fd0a3f3c                 "cat"                    53 seconds ago      Up 52 seconds                           k8s_helm_jenkins-slave-05zqj-0vwdh_jenkins_50b0c7ff-6756-11e8-ac97-080027a04784_0
ea5cd47f71f8        3f0ed001237f                 "jenkins-slave c3fad…"   53 seconds ago      Up 52 seconds                           k8s_jnlp_jenkins-slave-05zqj-0vwdh_jenkins_50b0c7ff-6756-11e8-ac97-080027a04784_0
f84e146f6486        94d70940f158                 "cat"                    53 seconds ago      Up 53 seconds                           k8s_kubectl_jenkins-slave-05zqj-0vwdh_jenkins_50b0c7ff-6756-11e8-ac97-080027a04784_0
6452a4f4762a        k8s.gcr.io/pause-amd64:3.1   "/pause"                 54 seconds ago      Up 53 seconds                           k8s_POD_jenkins-slave-05zqj-0vwdh_jenkins_50b0c7ff-6756-11e8-ac97-080027a04784_0
e9d8bd5ca98e        e94d2f21bc0c                 "/dashboard --insecu…"   24 minutes ago      Up 24 minutes                           k8s_kubernetes-dashboard_kubernetes-dashboard-5498ccf677-jf2sv_kube-system_ada3c72c-618b-11e8-b225-080027a04784_27
d9bc0309f06f        4689081edb10                 "/storage-provisioner"   24 minutes ago      Up 24 minutes                           k8s_storage-provisioner_storage-provisioner_kube-system_ae57df69-618b-11e8-b225-080027a04784_27
159fb1c9b149        jenkins/jenkins              "/sbin/tini -- /usr/…"   24 minutes ago      Up 24 minutes                           k8s_jenkins_jenkins-7455f75fb8-tpskq_jenkins_cd925a94-6689-11e8-af8a-080027a04784_2
6ec697cfa2bd        80cc5ea4b547                 "/kube-dns --domain=…"   24 minutes ago      Up 24 minutes                           k8s_kubedns_kube-dns-86f4d74b45-tvz6q_kube-system_acce4a41-618b-11e8-b225-080027a04784_18
a5a2ef20df67        bfc21aadc7d3                 "/usr/local/bin/kube…"   25 minutes ago      Up 25 minutes                           k8s_kube-proxy_kube-proxy-sb7fm_kube-system_ee3ecf8f-6752-11e8-ac97-080027a04784_0
87e2ccdcc798        k8s.gcr.io/pause-amd64:3.1   "/pause"                 25 minutes ago      Up 25 minutes                           k8s_POD_kube-proxy-sb7fm_kube-system_ee3ecf8f-6752-11e8-ac97-080027a04784_0
3004394fc9fb        6253045f26c6                 "/tiller"                26 minutes ago      Up 26 minutes                           k8s_tiller_tiller-deploy-f9b8476d-wfhzm_kube-system_4443f5b6-6671-11e8-8f97-080027a04784_4
e7c38531f7f8        k8s.gcr.io/pause-amd64:3.1   "/pause"                 26 minutes ago      Up 26 minutes                           k8s_POD_tiller-deploy-f9b8476d-wfhzm_kube-system_4443f5b6-6671-11e8-8f97-080027a04784_4
6251f868f5c8        6f7f2dc7fab5                 "/sidecar --v=2 --lo…"   26 minutes ago      Up 26 minutes                           k8s_sidecar_kube-dns-86f4d74b45-tvz6q_kube-system_acce4a41-618b-11e8-b225-080027a04784_9
fdee92e48e1b        c2ce1ffb51ed                 "/dnsmasq-nanny -v=2…"   26 minutes ago      Up 26 minutes                           k8s_dnsmasq_kube-dns-86f4d74b45-tvz6q_kube-system_acce4a41-618b-11e8-b225-080027a04784_9
835605dd4433        k8s.gcr.io/pause-amd64:3.1   "/pause"                 26 minutes ago      Up 26 minutes                           k8s_POD_storage-provisioner_kube-system_ae57df69-618b-11e8-b225-080027a04784_9
eb08a4b5f462        k8s.gcr.io/pause-amd64:3.1   "/pause"                 26 minutes ago      Up 26 minutes                           k8s_POD_kube-dns-86f4d74b45-tvz6q_kube-system_acce4a41-618b-11e8-b225-080027a04784_9
8f32f5972a5a        k8s.gcr.io/pause-amd64:3.1   "/pause"                 26 minutes ago      Up 26 minutes                           k8s_POD_kubernetes-dashboard-5498ccf677-jf2sv_kube-system_ada3c72c-618b-11e8-b225-080027a04784_9
852f5e0d112e        k8s.gcr.io/pause-amd64:3.1   "/pause"                 26 minutes ago      Up 26 minutes                           k8s_POD_jenkins-7455f75fb8-tpskq_jenkins_cd925a94-6689-11e8-af8a-080027a04784_2
bb488fd4b9d2        ad86dbed1555                 "kube-controller-man…"   26 minutes ago      Up 26 minutes                           k8s_kube-controller-manager_kube-controller-manager-minikube_kube-system_7f107f954a84047b7d86158148335dd7_0
b85804c57a11        52920ad46f5b                 "etcd --client-cert-…"   26 minutes ago      Up 26 minutes                           k8s_etcd_etcd-minikube_kube-system_d931e6c363c79ac15a6564bcfd732d66_0
52dee7885908        af20925d51a3                 "kube-apiserver --ad…"   26 minutes ago      Up 26 minutes                           k8s_kube-apiserver_kube-apiserver-minikube_kube-system_6dcf0ec047d146348b3771cc0329104b_0
aa02ff98591c        704ba848e69a                 "kube-scheduler --ku…"   26 minutes ago      Up 26 minutes                           k8s_kube-scheduler_kube-scheduler-minikube_kube-system_2acb197d598c4730e3f5b159b241a81b_0
cb58a7b3de4a        9c16409588eb                 "/opt/kube-addons.sh"    26 minutes ago      Up 26 minutes                           k8s_kube-addon-manager_kube-addon-manager-minikube_kube-system_3afaf06535cc3b85be93c31632b765da_9
35b89bb33680        k8s.gcr.io/pause-amd64:3.1   "/pause"                 26 minutes ago      Up 26 minutes                           k8s_POD_kube-scheduler-minikube_kube-system_2acb197d598c4730e3f5b159b241a81b_0
56d9ba1f3d30        k8s.gcr.io/pause-amd64:3.1   "/pause"                 26 minutes ago      Up 26 minutes                           k8s_POD_kube-controller-manager-minikube_kube-system_7f107f954a84047b7d86158148335dd7_0
7d10ed93214c        k8s.gcr.io/pause-amd64:3.1   "/pause"                 26 minutes ago      Up 26 minutes                           k8s_POD_etcd-minikube_kube-system_d931e6c363c79ac15a6564bcfd732d66_0
87cbe24572b8        k8s.gcr.io/pause-amd64:3.1   "/pause"                 26 minutes ago      Up 26 minutes                           k8s_POD_kube-apiserver-minikube_kube-system_6dcf0ec047d146348b3771cc0329104b_0
10d7db37b6df        k8s.gcr.io/pause-amd64:3.1   "/pause"                 26 minutes ago      Up 26 minutes                           k8s_POD_kube-addon-manager-minikube_kube-system_3afaf06535cc3b85be93c31632b765da_9
[Pipeline] sh
[mydevops-pipeline] Running shell script
+ chmod +x gradlew
[Pipeline] }
[Pipeline] // container
[Pipeline] }
[Pipeline] // stage
[Pipeline] stage
[Pipeline] { (Build)
[Pipeline] sh
[mydevops-pipeline] Running shell script
+ chmod +x gradlew
[Pipeline] sh
[mydevops-pipeline] Running shell script
+ ./gradlew clean build
Downloading https://services.gradle.org/distributions/gradle-4.3-bin.zip
.....................................................................
Unzipping /home/jenkins/.gradle/wrapper/dists/gradle-4.3-bin/452wx51oxqsia28686mgqhot6/gradle-4.3-bin.zip to /home/jenkins/.gradle/wrapper/dists/gradle-4.3-bin/452wx51oxqsia28686mgqhot6
Set executable permissions for: /home/jenkins/.gradle/wrapper/dists/gradle-4.3-bin/452wx51oxqsia28686mgqhot6/gradle-4.3/bin/gradle
Starting a Gradle Daemon (subsequent builds will be faster)
Download https://jcenter.bintray.com/com/palantir/jacoco-coverage/0.4.0/jacoco-coverage-0.4.0.pom
Download https://jcenter.bintray.com/org/springframework/boot/spring-boot-gradle-plugin/2.0.2.RELEASE/spring-boot-gradle-plugin-2.0.2.RELEASE.pom
Download https://jcenter.bintray.com/org/springframework/boot/spring-boot-tools/2.0.2.RELEASE/spring-boot-tools-2.0.2.RELEASE.pom
Download https://jcenter.bintray.com/org/springframework/boot/spring-boot-parent/2.0.2.RELEASE/spring-boot-parent-2.0.2.RELEASE.pom
Download https://jcenter.bintray.com/org/springframework/boot/spring-boot-dependencies/2.0.2.RELEASE/spring-boot-dependencies-2.0.2.RELEASE.pom
Download https://jcenter.bintray.com/com/fasterxml/jackson/jackson-bom/2.9.5/jackson-bom-2.9.5.pom
Download https://jcenter.bintray.com/com/fasterxml/jackson/jackson-parent/2.9.1/jackson-parent-2.9.1.pom
Download https://jcenter.bintray.com/com/fasterxml/oss-parent/30/oss-parent-30.pom
Download https://jcenter.bintray.com/io/netty/netty-bom/4.1.24.Final/netty-bom-4.1.24.Final.pom
Download https://jcenter.bintray.com/org/sonatype/oss/oss-parent/7/oss-parent-7.pom
Download https://jcenter.bintray.com/io/projectreactor/reactor-bom/Bismuth-SR9/reactor-bom-Bismuth-SR9.pom
Download https://jcenter.bintray.com/org/apache/logging/log4j/log4j-bom/2.10.0/log4j-bom-2.10.0.pom
Download https://jcenter.bintray.com/org/apache/logging/logging-parent/1/logging-parent-1.pom
Download https://jcenter.bintray.com/org/apache/apache/18/apache-18.pom
Download https://jcenter.bintray.com/org/eclipse/jetty/jetty-bom/9.4.10.v20180503/jetty-bom-9.4.10.v20180503.pom
Download https://jcenter.bintray.com/org/springframework/spring-framework-bom/5.0.6.RELEASE/spring-framework-bom-5.0.6.RELEASE.pom
Download https://jcenter.bintray.com/org/springframework/data/spring-data-releasetrain/Kay-SR7/spring-data-releasetrain-Kay-SR7.pom
Download https://jcenter.bintray.com/org/springframework/data/build/spring-data-build/2.0.7.RELEASE/spring-data-build-2.0.7.RELEASE.pom
Download https://jcenter.bintray.com/org/springframework/integration/spring-integration-bom/5.0.5.RELEASE/spring-integration-bom-5.0.5.RELEASE.pom
Download https://jcenter.bintray.com/org/springframework/security/spring-security-bom/5.0.5.RELEASE/spring-security-bom-5.0.5.RELEASE.pom
Download https://jcenter.bintray.com/org/springframework/session/spring-session-bom/Apple-SR2/spring-session-bom-Apple-SR2.pom
Download https://jcenter.bintray.com/com/google/guava/guava/18.0/guava-18.0.pom
Download https://jcenter.bintray.com/com/google/guava/guava-parent/18.0/guava-parent-18.0.pom
Download https://jcenter.bintray.com/io/spring/gradle/dependency-management-plugin/1.0.5.RELEASE/dependency-management-plugin-1.0.5.RELEASE.pom
Download https://jcenter.bintray.com/org/springframework/boot/spring-boot-loader-tools/2.0.2.RELEASE/spring-boot-loader-tools-2.0.2.RELEASE.pom
Download https://jcenter.bintray.com/org/apache/commons/commons-compress/1.14/commons-compress-1.14.pom
Download https://jcenter.bintray.com/org/apache/commons/commons-parent/42/commons-parent-42.pom
Download https://jcenter.bintray.com/org/springframework/spring-core/5.0.6.RELEASE/spring-core-5.0.6.RELEASE.pom
Download https://jcenter.bintray.com/org/springframework/spring-jcl/5.0.6.RELEASE/spring-jcl-5.0.6.RELEASE.pom
Download https://jcenter.bintray.com/org/springframework/boot/spring-boot-gradle-plugin/2.0.2.RELEASE/spring-boot-gradle-plugin-2.0.2.RELEASE.jar
Download https://jcenter.bintray.com/com/palantir/jacoco-coverage/0.4.0/jacoco-coverage-0.4.0.jar
Download https://jcenter.bintray.com/org/springframework/boot/spring-boot-loader-tools/2.0.2.RELEASE/spring-boot-loader-tools-2.0.2.RELEASE.jar
Download https://jcenter.bintray.com/org/apache/commons/commons-compress/1.14/commons-compress-1.14.jar
Download https://jcenter.bintray.com/io/spring/gradle/dependency-management-plugin/1.0.5.RELEASE/dependency-management-plugin-1.0.5.RELEASE.jar
Download https://jcenter.bintray.com/org/springframework/spring-core/5.0.6.RELEASE/spring-core-5.0.6.RELEASE.jar
Download https://jcenter.bintray.com/org/springframework/spring-jcl/5.0.6.RELEASE/spring-jcl-5.0.6.RELEASE.jar
Download https://jcenter.bintray.com/com/google/guava/guava/18.0/guava-18.0.jar
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-web/2.0.2.RELEASE/spring-boot-starter-web-2.0.2.RELEASE.pom
Download https://repo1.maven.org/maven2/org/projectlombok/lombok/1.16.20/lombok-1.16.20.pom
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starters/2.0.2.RELEASE/spring-boot-starters-2.0.2.RELEASE.pom
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-json/2.0.2.RELEASE/spring-boot-starter-json-2.0.2.RELEASE.pom
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter/2.0.2.RELEASE/spring-boot-starter-2.0.2.RELEASE.pom
Download https://repo1.maven.org/maven2/org/hibernate/validator/hibernate-validator/6.0.9.Final/hibernate-validator-6.0.9.Final.pom
Download https://repo1.maven.org/maven2/org/springframework/spring-web/5.0.6.RELEASE/spring-web-5.0.6.RELEASE.pom
Download https://repo1.maven.org/maven2/org/hibernate/validator/hibernate-validator-parent/6.0.9.Final/hibernate-validator-parent-6.0.9.Final.pom
Download https://repo1.maven.org/maven2/org/springframework/spring-webmvc/5.0.6.RELEASE/spring-webmvc-5.0.6.RELEASE.pom
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-tomcat/2.0.2.RELEASE/spring-boot-starter-tomcat-2.0.2.RELEASE.pom
Download https://repo1.maven.org/maven2/org/jboss/arquillian/arquillian-bom/1.1.11.Final/arquillian-bom-1.1.11.Final.pom
Download https://repo1.maven.org/maven2/org/jboss/shrinkwrap/shrinkwrap-bom/1.2.3/shrinkwrap-bom-1.2.3.pom
Download https://repo1.maven.org/maven2/org/jboss/shrinkwrap/resolver/shrinkwrap-resolver-bom/2.2.0/shrinkwrap-resolver-bom-2.2.0.pom
Download https://repo1.maven.org/maven2/org/jboss/shrinkwrap/descriptors/shrinkwrap-descriptors-bom/2.0.0-alpha-8/shrinkwrap-descriptors-bom-2.0.0-alpha-8.pom
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-autoconfigure/2.0.2.RELEASE/spring-boot-autoconfigure-2.0.2.RELEASE.pom
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/2.0.2.RELEASE/spring-boot-2.0.2.RELEASE.pom
Download https://repo1.maven.org/maven2/javax/annotation/javax.annotation-api/1.3.2/javax.annotation-api-1.3.2.pom
Download https://repo1.maven.org/maven2/net/java/jvnet-parent/3/jvnet-parent-3.pom
Download https://repo1.maven.org/maven2/org/yaml/snakeyaml/1.19/snakeyaml-1.19.pom
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-logging/2.0.2.RELEASE/spring-boot-starter-logging-2.0.2.RELEASE.pom
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/datatype/jackson-datatype-jdk8/2.9.5/jackson-datatype-jdk8-2.9.5.pom
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.9.5/jackson-databind-2.9.5.pom
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/module/jackson-modules-java8/2.9.5/jackson-modules-java8-2.9.5.pom
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-base/2.9.5/jackson-base-2.9.5.pom
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/module/jackson-module-parameter-names/2.9.5/jackson-module-parameter-names-2.9.5.pom
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.9.5/jackson-datatype-jsr310-2.9.5.pom
Download https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-el/8.5.31/tomcat-embed-el-8.5.31.pom
Download https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-core/8.5.31/tomcat-embed-core-8.5.31.pom
Download https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-websocket/8.5.31/tomcat-embed-websocket-8.5.31.pom
Download https://repo1.maven.org/maven2/javax/validation/validation-api/2.0.1.Final/validation-api-2.0.1.Final.pom
Download https://repo1.maven.org/maven2/org/jboss/logging/jboss-logging/3.3.2.Final/jboss-logging-3.3.2.Final.pom
Download https://repo1.maven.org/maven2/com/fasterxml/classmate/1.3.4/classmate-1.3.4.pom
Download https://repo1.maven.org/maven2/org/jboss/jboss-parent/15/jboss-parent-15.pom
Download https://repo1.maven.org/maven2/com/fasterxml/oss-parent/24/oss-parent-24.pom
Download https://repo1.maven.org/maven2/org/springframework/spring-beans/5.0.6.RELEASE/spring-beans-5.0.6.RELEASE.pom
Download https://repo1.maven.org/maven2/org/springframework/spring-aop/5.0.6.RELEASE/spring-aop-5.0.6.RELEASE.pom
Download https://repo1.maven.org/maven2/org/springframework/spring-context/5.0.6.RELEASE/spring-context-5.0.6.RELEASE.pom
Download https://repo1.maven.org/maven2/org/springframework/spring-expression/5.0.6.RELEASE/spring-expression-5.0.6.RELEASE.pom
Download https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.2.3/logback-classic-1.2.3.pom
Download https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-to-slf4j/2.10.0/log4j-to-slf4j-2.10.0.pom
Download https://repo1.maven.org/maven2/ch/qos/logback/logback-parent/1.2.3/logback-parent-1.2.3.pom
Download https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j/2.10.0/log4j-2.10.0.pom
Download https://repo1.maven.org/maven2/org/slf4j/jul-to-slf4j/1.7.25/jul-to-slf4j-1.7.25.pom
Download https://repo1.maven.org/maven2/org/slf4j/slf4j-parent/1.7.25/slf4j-parent-1.7.25.pom
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.pom
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.9.5/jackson-core-2.9.5.pom
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.9.0/jackson-parent-2.9.0.pom
Download https://repo1.maven.org/maven2/com/fasterxml/oss-parent/28/oss-parent-28.pom
Download https://repo1.maven.org/maven2/ch/qos/logback/logback-core/1.2.3/logback-core-1.2.3.pom
Download https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.pom
Download https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.10.0/log4j-api-2.10.0.pom
Download https://repo1.maven.org/maven2/org/apache/tomcat/tomcat-annotations-api/8.5.31/tomcat-annotations-api-8.5.31.pom
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-web/2.0.2.RELEASE/spring-boot-starter-web-2.0.2.RELEASE.jar
Download https://repo1.maven.org/maven2/org/projectlombok/lombok/1.16.20/lombok-1.16.20.jar
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter/2.0.2.RELEASE/spring-boot-starter-2.0.2.RELEASE.jar
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-tomcat/2.0.2.RELEASE/spring-boot-starter-tomcat-2.0.2.RELEASE.jar
Download https://repo1.maven.org/maven2/org/hibernate/validator/hibernate-validator/6.0.9.Final/hibernate-validator-6.0.9.Final.jar
Download https://repo1.maven.org/maven2/org/springframework/spring-webmvc/5.0.6.RELEASE/spring-webmvc-5.0.6.RELEASE.jar
Download https://repo1.maven.org/maven2/org/springframework/spring-web/5.0.6.RELEASE/spring-web-5.0.6.RELEASE.jar
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-autoconfigure/2.0.2.RELEASE/spring-boot-autoconfigure-2.0.2.RELEASE.jar
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/2.0.2.RELEASE/spring-boot-2.0.2.RELEASE.jar
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-logging/2.0.2.RELEASE/spring-boot-starter-logging-2.0.2.RELEASE.jar
Download https://repo1.maven.org/maven2/javax/annotation/javax.annotation-api/1.3.2/javax.annotation-api-1.3.2.jar
Download https://repo1.maven.org/maven2/org/springframework/spring-context/5.0.6.RELEASE/spring-context-5.0.6.RELEASE.jar
Download https://repo1.maven.org/maven2/org/springframework/spring-aop/5.0.6.RELEASE/spring-aop-5.0.6.RELEASE.jar
Download https://repo1.maven.org/maven2/org/springframework/spring-beans/5.0.6.RELEASE/spring-beans-5.0.6.RELEASE.jar
Download https://repo1.maven.org/maven2/org/springframework/spring-expression/5.0.6.RELEASE/spring-expression-5.0.6.RELEASE.jar
Download https://repo1.maven.org/maven2/org/yaml/snakeyaml/1.19/snakeyaml-1.19.jar
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/datatype/jackson-datatype-jdk8/2.9.5/jackson-datatype-jdk8-2.9.5.jar
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.9.5/jackson-datatype-jsr310-2.9.5.jar
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/module/jackson-module-parameter-names/2.9.5/jackson-module-parameter-names-2.9.5.jar
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.9.5/jackson-databind-2.9.5.jar
Download https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-websocket/8.5.31/tomcat-embed-websocket-8.5.31.jar
Download https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-core/8.5.31/tomcat-embed-core-8.5.31.jar
Download https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-el/8.5.31/tomcat-embed-el-8.5.31.jar
Download https://repo1.maven.org/maven2/javax/validation/validation-api/2.0.1.Final/validation-api-2.0.1.Final.jar
Download https://repo1.maven.org/maven2/org/jboss/logging/jboss-logging/3.3.2.Final/jboss-logging-3.3.2.Final.jar
Download https://repo1.maven.org/maven2/com/fasterxml/classmate/1.3.4/classmate-1.3.4.jar
Download https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-to-slf4j/2.10.0/log4j-to-slf4j-2.10.0.jar
Download https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.2.3/logback-classic-1.2.3.jar
Download https://repo1.maven.org/maven2/org/slf4j/jul-to-slf4j/1.7.25/jul-to-slf4j-1.7.25.jar
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar
Download https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.9.5/jackson-core-2.9.5.jar
Download https://repo1.maven.org/maven2/ch/qos/logback/logback-core/1.2.3/logback-core-1.2.3.jar
Download https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar
Download https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.10.0/log4j-api-2.10.0.jar
Download https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-json/2.0.2.RELEASE/spring-boot-starter-json-2.0.2.RELEASE.jar
:clean UP-TO-DATE
:compileJava
:processResources
:classes
:bootJar
:jar SKIPPED
:assemble
:compileTestJava NO-SOURCE
:processTestResources NO-SOURCE
:testClasses UP-TO-DATE
:test NO-SOURCE
:check UP-TO-DATE
:build

BUILD SUCCESSFUL in 1m 43s
4 actionable tasks: 3 executed, 1 up-to-date
[Pipeline] }
[Pipeline] // stage
[Pipeline] stage
[Pipeline] { (Create Docker images)
[Pipeline] container
[Pipeline] {
[Pipeline] withCredentials
[Pipeline] {
[Pipeline] sh
[mydevops-pipeline] Running shell script
+ docker login -u **** -p ****
WARNING! Using --password via the CLI is insecure. Use --password-stdin.
WARNING! Your password will be stored unencrypted in /home/jenkins/.docker/config.json.
Configure a credential helper to remove this warning. See
https://docs.docker.com/engine/reference/commandline/login/#credentials-store

Login Succeeded
+ docker build -t ****/myimage:43c01b4c6ce4879f293afd25d3356a1cacee2c3e .
Sending build context to Docker daemon  18.03MB

Step 1/5 : FROM frolvlad/alpine-oraclejdk8:slim
 ---> d181699b91d1
Step 2/5 : EXPOSE 9090
 ---> Using cache
 ---> cbf9401dafb2
Step 3/5 : RUN mkdir -p /app/
 ---> Using cache
 ---> 5121e3300c00
Step 4/5 : ADD build/libs/mydevopsapi-v1-0.0.1-SNAPSHOT.jar /app/mydevopsapi-v1.jar
 ---> df946e01e14a
Step 5/5 : ENTRYPOINT ["java", "-jar", "/app/mydevopsapi-v1.jar"]
 ---> Running in 471a431e2a31
Removing intermediate container 471a431e2a31
 ---> f0b406a1dedd
Successfully built f0b406a1dedd
Successfully tagged ****/myimage:43c01b4c6ce4879f293afd25d3356a1cacee2c3e
+ docker push ****/myimage:43c01b4c6ce4879f293afd25d3356a1cacee2c3e
The push refers to repository [docker.io/****/myimage]
66903709feb9: Preparing
24813051afe3: Preparing
58fd67c6d05b: Preparing
14eb2272e922: Preparing
cd7100a72410: Preparing
14eb2272e922: Layer already exists
24813051afe3: Layer already exists
cd7100a72410: Layer already exists
58fd67c6d05b: Layer already exists
66903709feb9: Pushed
43c01b4c6ce4879f293afd25d3356a1cacee2c3e: digest: sha256:501339dbf6b2065979ba1e57819c4f5fbf82f1b931003865e6b0e2da10b8a00b size: 1369
[Pipeline] }
[Pipeline] // withCredentials
[Pipeline] }
[Pipeline] // container
[Pipeline] }
[Pipeline] // stage
[Pipeline] stage
[Pipeline] { (Run kubectl)
[Pipeline] container
[Pipeline] {
[Pipeline] sh
[mydevops-pipeline] Running shell script
+ kubectl get pods
NAME                        READY     STATUS    RESTARTS   AGE
jenkins-7455f75fb8-tpskq    1/1       Running   2          1d
jenkins-slave-05zqj-0vwdh   4/4       Running   0          4m
[Pipeline] }
[Pipeline] // container
[Pipeline] }
[Pipeline] // stage
[Pipeline] stage
[Pipeline] { (Run helm)
[Pipeline] container
[Pipeline] {
[Pipeline] sh
[mydevops-pipeline] Running shell script
+ helm list
NAME   	REVISION	UPDATED                 	STATUS  	CHART         	NAMESPACE
jenkins	1       	Sat Jun  2 17:24:27 2018	DEPLOYED	jenkins-0.16.1	jenkins  
[Pipeline] }
[Pipeline] // container
[Pipeline] }
[Pipeline] // stage
[Pipeline] }
[Pipeline] // node
[Pipeline] }
[Pipeline] // podTemplate
[Pipeline] End of Pipeline
Finished: SUCCESS

 

Go to Docker Hub and check whether image has been pushed or not-

