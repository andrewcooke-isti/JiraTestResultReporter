#!/bin/bash

if [ `basename $PWD` != "JiraTestResultReporter" ]; then
    echo "This script must be run from the JiraTestResultReporter directory" 1>&2
    exit 1
fi

mvn clean compile assembly:single
