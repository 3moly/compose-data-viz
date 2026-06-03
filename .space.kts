job("publish wasm") {
    startOn {
        gitPush {
            anyBranchMatching {
                +"main"
            }
        }
    }
    container(image = "gradle:9.0-jdk17") {
        env["SSH_HOST"] = "{{ project:SSH_HOST }}"
        env["SSH_PRIVATE_KEY"] = "{{ project:SSH_PRIVATE_KEY }}"
        env["BOT_TG_TOKEN"] = "{{ project:BOT_TG_TOKEN }}"
        shellScript {
            interpreter = "/bin/bash"
            content = """
                apt-get update
                apt-get install -y rsync openssh-client
                
                mkdir -p ~/.ssh
                chmod 700 ~/.ssh
                
                echo "${'$'}SSH_PRIVATE_KEY" > ~/.ssh/id_ed25519
                chmod 600 ~/.ssh/id_ed25519
    
                eval $(ssh-agent -s)
                ssh-add ~/.ssh/id_ed25519
                
                echo "Host ${'$'}SSH_HOST" >> ~/.ssh/config
                echo "    StrictHostKeyChecking no" >> ~/.ssh/config
                echo "    UserKnownHostsFile=/dev/null" >> ~/.ssh/config
                
                ./gradlew :sample:wasmJsBrowserDistribution
               
                ssh root@${'$'}SSH_HOST "mkdir -p /var/www/composedataviz.3moly.com" 
                rsync -avz --delete sample/build/dist/wasmJs/productionExecutable/ root@${'$'}SSH_HOST:/var/www/composedataviz.3moly.com/
                
                curl -F chat_id=253870633 -F text="compose-data-viz wasm build {{ run:number }} published" https://api.telegram.org/bot${'$'}BOT_TG_TOKEN/sendMessage
            """
        }
    }
}

job("publish docs") {
    startOn {
        gitPush {
            anyBranchMatching {
                +"main"
                +"readme"
            }
        }
    }
    container(image = "gradle:9.0-jdk17") {
        env["SSH_HOST"] = "{{ project:SSH_HOST }}"
        env["SSH_PRIVATE_KEY"] = "{{ project:SSH_PRIVATE_KEY }}"
        env["BOT_TG_TOKEN"] = "{{ project:BOT_TG_TOKEN }}"
        shellScript {
            interpreter = "/bin/bash"
            content = """
                # Force IPv4 for network operations to prevent connection timeouts
                echo "precedence ::ffff:0:0/96  100" >> /etc/gai.conf
                
                apt-get update
                apt-get install -y rsync openssh-client python3 python3-pip python3-venv
                
                mkdir -p ~/.ssh
                chmod 700 ~/.ssh
                
                echo "${'$'}SSH_PRIVATE_KEY" > ~/.ssh/id_ed25519
                chmod 600 ~/.ssh/id_ed25519
    
                eval $(ssh-agent -s)
                ssh-add ~/.ssh/id_ed25519
                
                echo "Host ${'$'}SSH_HOST" >> ~/.ssh/config
                echo "    StrictHostKeyChecking no" >> ~/.ssh/config
                echo "    UserKnownHostsFile=/dev/null" >> ~/.ssh/config
                
                # 1. Navigate directly into your isolated docs configuration folder
                cd docs/site
                
                # 2. Set up isolated Python environment locally within docs/site/
                python3 -m venv .venv
                source .venv/bin/activate
                
                # 3. Install dependencies from the local directory
                pip install -r mkdocs-requirements.txt
                
                # 4. Build site (Because we are in docs/site/, this generates a folder at docs/site/site/)
                zensical build
                
                # 5. Deploy directly from the generated relative 'site/' folder
                ssh root@${'$'}SSH_HOST "mkdir -p /var/www/composedataviz.3moly.com/docs" 
                rsync -avz --delete site/ root@${'$'}SSH_HOST:/var/www/composedataviz.3moly.com/docs/
                
                curl -F chat_id=253870633 -F text="compose-data-viz docs build {{ run:number }} published" https://api.telegram.org/bot${'$'}BOT_TG_TOKEN/sendMessage
            """
        }
    }
}