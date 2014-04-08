JiraTestResultReporter
======================

This is a fork of the [original
JiraTestResultReporter](https://github.com/maplesteve/JiraTestResultReporter)
(MIT Licenced) which we hope to get running with recent Jira using the
[JRJC](https://ecosystem.atlassian.net/wiki/display/JRJC/Home).

The changes to the original include:

* The plugin actually works with the latest Jenkins and Jira (April
  2014)!

* Calls to Jira are made via the [Jira Rest
  Client](https://ecosystem.atlassian.net/wiki/display/JRJC/Home).

* The issue type is configurable.

* A command line interface, `com.isti.jira.CmdLine`, is available that
  provides similar functionality (making debugging initial
  configuration much easier, since you can test directly from the
  command line).

  Some example output:

```
[cats@localhost jira-plugin]$ ./cmd-line-target.sh help create-issue
NAME
        CmdLine create-issue - Create a new issue

SYNOPSIS
        CmdLine [-H <url>] [-P <password>] [-U <user>] create-issue
                [-d <description>] [-p <project>] [-s <summary>] [-t <type>]

OPTIONS
        -d <description>
            Description

        -H <url>
            Jira URL

        -p <project>
            Project ID

        -P <password>
            Password to connect with

        -s <summary>
            Summary

        -t <type>
            Issue type

        -U <user>
            User to connect as
```

* Often-used configuration options can be given in a properties file
  in `/var/lib/jenkins/.catsjira` (or in the home directory of
  whichever user is running the plugin).  This avoids having to repeat
  the configuration for each test and allows access from the command
  line tool.

  A typical configuration file might look like:

```
user=cats
password=secret
url=http://localhost:8081
issue_type=bug
```

## Installation

* Compile and install the plugin as normal (or see the scripts in
  `src/main/bash`).

* Optionally, add `.catsjira` to `/var/lib/jenkins` with defaults.

* Use command line tool to check that projects and issue types can be
  listed.

* Configure the plugin for a particular test (it's added as a
  post-build step) from the usual drop-down menu.

* Initially, select "Create issue for all errors" and run the test
  once (if you have current errors that you want to report).

* Disable "Create issue for all errors" so that only new errors are
  reported on subsequent runs.

## Planned Work

Future changes should include:

* Closing issues when the test works again.

