FROM openjdk:11

# Install mongodump https://docs.mongodb.com/manual/tutorial/install-mongodb-on-debian/
RUN wget -qO - https://www.mongodb.org/static/pgp/server-4.2.asc | apt-key add -
RUN echo "deb http://repo.mongodb.org/apt/debian stretch/mongodb-org/4.2 main" | tee /etc/apt/sources.list.d/mongodb-org-4.2.list
RUN apt-get update
RUN apt-get install -y mongodb-org-tools=4.2.2

EXPOSE 8080
WORKDIR /opt/mongodumper
CMD ["java", "-jar", "mongodumper.jar"]
COPY ./mongodumper.jar /opt/mongodumper/mongodumper.jar
