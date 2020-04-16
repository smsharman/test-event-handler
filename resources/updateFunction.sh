#!/usr/bin/env bash
aws lambda update-function-code \
    --region eu-west-1 \
    --function-name synergy-test-handler \
    --cli-connect-timeout 6000 \
    --zip-file fileb://$(pwd)/../target/synergy-test-handler.jar