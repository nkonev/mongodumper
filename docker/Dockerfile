FROM openjdk:11

# Install keys & repository
RUN wget -qO - https://www.mongodb.org/static/pgp/server-4.2.asc | apt-key add - && \
 echo "deb http://repo.mongodb.org/apt/debian stretch/mongodb-org/4.2 main" | tee /etc/apt/sources.list.d/mongodb-org-4.2.list
# Install mongodump https://docs.mongodb.com/manual/tutorial/install-mongodb-on-debian/ & net-tools
RUN apt-get update && \
 apt-get install -y mongodb-org-tools=4.2.2 mongodb-org-shell=4.2.2 net-tools

EXPOSE 8080
WORKDIR /opt/mongodumper
ENTRYPOINT ["/entrypoint.sh"]
COPY entrypoint.sh /entrypoint.sh
COPY ./mongodumper.jar /opt/mongodumper/mongodumper.jar
