#!/usr/bin/env bash

# LocalStack initialization script for SeatForge.
# Runs inside the container on startup.

set -euo pipefail

ENDPOINT_URL="http://localhost:4566"
REGION="us-east-1"
TOPIC_NAME="seatforge-bookings"
QUEUE_NAME="seatforge-notifications"
DLQ_NAME="seatforge-notifications-dlq"

echo "[init-aws] waiting for LocalStack to accept requests"
until aws --endpoint-url="${ENDPOINT_URL}" --region="${REGION}" sqs list-queues >/dev/null 2>&1; do
    sleep 1
done
echo "[init-aws] LocalStack is up"

# --- Create DLQ ---
echo "[init-aws] creating DLQ ${DLQ_NAME}"
DLQ_URL=$(aws --endpoint-url="${ENDPOINT_URL}" --region="${REGION}" \
    sqs create-queue \
    --queue-name "${DLQ_NAME}" \
    --query 'QueueUrl' --output text)

DLQ_ARN=$(aws --endpoint-url="${ENDPOINT_URL}" --region="${REGION}" \
    sqs get-queue-attributes \
    --queue-url "${DLQ_URL}" \
    --attribute-names QueueArn \
    --query 'Attributes.QueueArn' --output text)

# --- Build the RedrivePolicy as a JSON file ---
# The AWS CLI accepts file:// references for structured parameters.
# This avoids all shell-quoting problems with nested JSON.
ATTR_FILE=$(mktemp)
cat > "${ATTR_FILE}" <<EOF
{
  "RedrivePolicy": "{\"deadLetterTargetArn\":\"${DLQ_ARN}\",\"maxReceiveCount\":\"3\"}"
}
EOF

echo "[init-aws] creating main queue ${QUEUE_NAME} with redrive policy"
QUEUE_URL=$(aws --endpoint-url="${ENDPOINT_URL}" --region="${REGION}" \
    sqs create-queue \
    --queue-name "${QUEUE_NAME}" \
    --attributes "file://${ATTR_FILE}" \
    --query 'QueueUrl' --output text)

rm -f "${ATTR_FILE}"

# --- Create SNS topic ---
echo "[init-aws] creating SNS topic ${TOPIC_NAME}"
TOPIC_ARN=$(aws --endpoint-url="${ENDPOINT_URL}" --region="${REGION}" \
    sns create-topic \
    --name "${TOPIC_NAME}" \
    --query 'TopicArn' --output text)

# --- Subscribe the SQS queue to the SNS topic ---
QUEUE_ARN=$(aws --endpoint-url="${ENDPOINT_URL}" --region="${REGION}" \
    sqs get-queue-attributes \
    --queue-url "${QUEUE_URL}" \
    --attribute-names QueueArn \
    --query 'Attributes.QueueArn' --output text)

echo "[init-aws] subscribing ${QUEUE_NAME} to ${TOPIC_NAME}"
aws --endpoint-url="${ENDPOINT_URL}" --region="${REGION}" \
    sns subscribe \
    --topic-arn "${TOPIC_ARN}" \
    --protocol sqs \
    --notification-endpoint "${QUEUE_ARN}" \
    --attributes '{"RawMessageDelivery":"true"}'

echo "[init-aws] initialization complete"
echo "[init-aws] Topic ARN: ${TOPIC_ARN}"
echo "[init-aws] Queue URL: ${QUEUE_URL}"
echo "[init-aws] DLQ URL:   ${DLQ_URL}"