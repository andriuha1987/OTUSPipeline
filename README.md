# OTUSPipeline

docker-compose up -d   (-f путь до файла /home/vboxuser/jenkins/docker-compose.yml)

docker run -i --rm --name agent --init jenkins/agent java -jar /usr/share/jenkins/agent.jar

jenkins-jobs --conf ./jobs.ini update ./jobs