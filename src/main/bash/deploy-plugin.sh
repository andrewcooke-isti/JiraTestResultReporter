#!/bin/bash

if [ `basename $PWD` != "JiraTestResultReporter" ]; then
    echo "This script must be run from the JiraTestResultReporter directory" 1>&2
    exit 1
fi

# https://wiki.jenkins-ci.org/display/JENKINS/Plugin+tutorial
mvn install
sudo service jenkins stop
sudo cp target/JiraTestResultReporter.hpi /var/lib/jenkins/plugins/
sudo rm -fr  /var/lib/jenkins/plugins/JiraTestResultReporter
sudo touch /var/lib/jenkins/plugins/JiraTestResultReporter.hpi.pinned
sudo service jenkins start
