#!/bin/bash

if [ `basename $PWD` != "JiraTestResultReporter" ]; then
    echo "This script must be run from the JiraTestResultReporter directory" 1>&2
    exit 1
fi

java -cp "target/jenkins-for-test/WEB-INF/lib/*":"target/JiraTestResultReporter/WEB-INF/lib/*":target/classes com.isti.jira.CmdLine $@
