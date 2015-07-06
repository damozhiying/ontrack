# Base
FROM java:openjdk-8u45-jdk

# Meta-information
MAINTAINER Damien Coraboeuf <damien.coraboeuf@gmail.com>

# Git installation
RUN apt-get install -y git

# Directory set-up
RUN mkdir -p /opt/ontrack

# External volume set-up and links
VOLUME /var/ontrack

# Exposing the port
EXPOSE 8080

# Profile
ENV PROFILE prod

# Copies the Ontrack JAR
COPY ontrack.jar /opt/ontrack/

# Running the application
WORKDIR /var/ontrack
CMD ["java", "-jar", "/opt/ontrack/ontrack.jar", "--spring.profiles.active=${PROFILE}", "--logging.path=log/"]