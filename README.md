JiraTestResultReporter
======================

This is a fork of the [original
JiraTestResultReporter](https://github.com/maplesteve/JiraTestResultReporter)
(MIT Licenced) which now talks to Jira using the
[JRJC](https://ecosystem.atlassian.net/wiki/display/JRJC/Home).

## Features

* Automatically opens and closes Jira issues when tests fail.

* Works with JUnit, XUnit and TAP plugins (and hopefully others).

* Calls to Jira are made via the [Jira Rest
  Client](https://ecosystem.atlassian.net/wiki/display/JRJC/Home).

* Issues are only created if Jira doesn't contains an unresolved,
  matching issue.

* The issue type and closing transition are configurable.

* A command line interface, `com.isti.jira.CmdLine` makes
  debugging initial configuration easy.

* Often-used configuration options can be given in a properties file
  in `/var/lib/jenkins/.jira-remote` (or in the home directory of
  whichever user is running the plugin).  This avoids having to repeat
  the configuration for each test and allows access from the command
  line tool.

## UnFeatures

The plugin currently assumes that git is used, and stores information in
four Jira fields - "CATS repository", "CATS branch", "CATS hash" and
"CATS commit".

## Configuration

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

* Optionally, add `.jira-remote` to `/var/lib/jenkins` with defaults.

* Use command line tool to check that projects and issue types can be
  listed.

* Configure the plugin for a particular test (it's added as a
  post-build step, after using the JUnit / XUnit, TAP plugin) from 
  the usual drop-down menu.

* Initially, select "Create issue for all errors" and run the test
  once (if you have current errors that you want to report).

* Disable "Create issue for all errors" so that only new errors are
  reported on subsequent runs.
