#!/usr/bin/bash

PID=$(ps -ax | grep -i java | grep -v grep | awk '{print $1}')
echo "stopping pid $PID"

kill $PID


