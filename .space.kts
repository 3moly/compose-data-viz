job("publish wasm") {
    startOn {
        gitPush {
            anyBranchMatching {
                +"main"
            }
        }
    }
    container(image = "gradle:9.0-jdk17"){
            env["SSH_HOST"] = "{{ project:SSH_HOST }}"
            env["SSH_PRIVATE_KEY"] = "{{ project:SSH_PRIVATE_KEY }}"
            env["BOT_TG_TOKEN"] = "{{ project:BOT_TG_TOKEN }}"
            shellScript {
                interpreter = "/bin/bash"
                content = """
                    apt-get update
                    apt install -y python3 python3-pip rsync openssh-client
                    apt-get update -y
                    apt-get install -y rsync
                    apt-cache search rsync # check if apt can find rsync
                    apt update
                    apt install -y python3
                    apt install -y python3-pip
                    
                    # Create SSH directory and set permissions
                    mkdir -p ~/.ssh
                    chmod 700 ~/.ssh
                    
                    # Add the private key
                    echo "${'$'}SSH_PRIVATE_KEY" > ~/.ssh/id_ed25519
                    chmod 600 ~/.ssh/id_ed25519
        
                    # Add the private key to the SSH agent
                    eval $(ssh-agent -s)
                    ssh-add ~/.ssh/id_ed25519
                    
                    # Disable host key checking for this session
                    echo "Host ${'$'}SSH_HOST" >> ~/.ssh/config
                    echo "    StrictHostKeyChecking no" >> ~/.ssh/config
                    echo "    UserKnownHostsFile=/dev/null" >> ~/.ssh/config
                    # apt-get update
                    # apt-get install -y openssh-client
                    echo The working directory is
                    echo ${'$'}JB_SPACE_WORK_DIR_PATH
                    echo ssh_host: ${'$'}SSH_HOST
                   
                    ./gradlew :wasmJsBrowserDevelopmentRun
                    
                    ssh root@${'$'}SSH_HOST "mkdir -p /var/www/obsidiandemo.3moly.com" 
                    rsync -avz --delete ${'$'}JB_SPACE_WORK_DIR_PATH/sample/build/dist/wasmJs/productionExecutable root@${'$'}SSH_HOST:/var/www/obsidiandemo.3moly.com/
                    
                    curl -F chat_id=253870633 -F text="compose-data-viz build {{ run:number }}" https://api.telegram.org/bot${'$'}BOT_TG_TOKEN/sendMessage
                """
            }
    }
}