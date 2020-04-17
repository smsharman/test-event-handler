#!/bin/bash
aws lambda create-function \
    --region eu-west-1 \
    --function-name synergy-test-handler \
    --zip-file fileb://$(pwd)/../target/synergy-test-handler.jar \
    --role arn:aws:iam::979590819078:role/syn-evt-lambda \
    --handler test-event-handler.core.Route \
    --runtime java11 \
    --timeout 15 \
    --memory-size 1024 \
    --cli-connect-timeout 6000