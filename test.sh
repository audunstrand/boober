#!/usr/bin/env bash

token=$(oc whoami -t)
#http --timeout 300 PUT :8080/setup Authorization:"bearer $token"  < files.json
http --timeout 300 PUT :8080/auroraconfig/paas Authorization:"bearer $token"  < auroraconfig.json

