#!/bin/bash

if which yum > /dev/null; then
  wget -O /etc/yum.repos.d/jenkins.repo http://pkg.jenkins-ci.org/redhat/jenkins.repo
  rpm --import http://pkg.jenkins-ci.org/redhat/jenkins-ci.org.key
elif which apt-get > /dev/null; then
  wget -q -O - http://pkg.jenkins-ci.org/debian/jenkins-ci.org.key | apt-key add -
  echo "deb http://pkg.jenkins-ci.org/debian binary/" >> /etc/apt/sources.list
  apt-get update
elif which zypper > /dev/null; then
  zypper addrepo http://pkg.jenkins-ci.org/opensuse/ jenkins
fi
