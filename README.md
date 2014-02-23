Deploy website package

Login into EC2 instance (username/password is bitnami/bitnami)

ssh -i ~/.ssh/ticketanchor-demo.pem bitnami@54.213.232.192

Get code from github.com

wget https://codeload.github.com/ticketanchor/ticketanchor.com/zip/master -O ticketanchor.com.zip

Unzip the downloaded file into “ticketanchor.com-master”

unzip ticketanchor.com.zip

build the package

cd ~/ticketanchor.com-master
mvn clean install -DskipTests=true

copy the ROOT.war from ~/ticketanchor.com/site/target/ROOT.war to /opt/bitnami/apache-tomcat/webapps

sudo  cp ~/ticketanchor.com-master/site/target/ROOT.war  /opt/bitnami/apache-tomcat/webapps/

tomcat will redeploy the ROOT war package

Deploy admin package
	The same as the above steps, but for step 5:
	
sudo  cp ~/ticketanchor.com-master/site/target/admin.war  /opt/bitnami/apache-tomcat/webapps/
