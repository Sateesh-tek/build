pipeline{
    agent any
    environment{
	//environment setup with the Anypoint Credentials(referenced from Global Jenkins credentials) and APP Repository
        AP_CREDENTIALS=credentials('Anypoint') 
		APP_REPO='https://gitlab.blr.teksystems.com/amagarwal/devops-test.git'
		ANYPOINT_URI='https://anypoint.mulesoft.com/accounts/login'
    }
	parameters {
		string(name: 'APP_BRANCH', defaultValue: 'develop', description: 'Branch from which the code is to be checked out')
    }
    stages{
        stage('Declarative: Checkout SCM'){
            steps{
                 echo "Start Checkout SCM"
				 checkout(
					[
						$class: 'GitSCM',
						branches: [[name: "${env.APP_BRANCH}"]],
						doGenerateSubmoduleConfigurations: false,
						extensions: [[$class: 'CleanCheckout']],
						submoduleCfg: [],
						userRemoteConfigs: 
						[
							[
								credentialsId: 'GITLabs_Password', 
								url: "${APP_REPO}"
							]
						]
					])
					echo "Finished Checkout SCM"
            }
        }     
		stage('BootStrap target configuration'){
		//injects required properties file at workspace
            steps{  
				echo "Start BootStrap target configuration"
                configFileProvider([configFile(fileId: "devops-dev.properties", targetLocation: "${WORKSPACE}")]) {}
                configFileProvider([configFile(fileId: "settings.xml",targetLocation: "${WORKSPACE}")]) {}
				echo " Finished BootStrap target configuration"
            }
        }
        stage('Set Version'){
		// sets artifact version in reference to the successful build (BUILD_ID is an environment variable listing build specific number, used to set version)
            steps{        
				echo "Start Set Version"
				bat "mvn versions:set -DnewVersion=1.0.${BUILD_ID} -f pom.xml"      
				echo "Finished Set Version"			
            }
        }
        stage('Munit Test'){
		//Munit Tests run and properties set to devops-dev.properties
            steps{  
                bat "mvn clean test -Dmaven.properties=devops-dev.properties"
            }       
        }
        stage('Maven Build and Deploy to Exchange'){
			steps{ 
                script{  
					 //command generates access token required to deploy to Exchange
					 def response = readJSON text: sh(script:"curl -k -d 'username=$AP_CREDENTIALS_USR&password=$AP_CREDENTIALS_PSW' ${ANYPOINT_URI}", returnStdout: true)
					 //command is used to deploy to Exchange
					 bat "mvn deploy -s settings.xml -Dtoken=${response.access_token} -DrepositoryId=exchange-repository -Dmaven.properties=devops-dev.properties -DskipTests"			     
				}			
			}
        }
	} 
	post {
        always {
                cleanWs()
        }
    }
}