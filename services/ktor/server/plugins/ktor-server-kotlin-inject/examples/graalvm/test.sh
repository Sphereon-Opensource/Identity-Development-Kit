#!/bin/bash

# Test script for GraalVM Native Image example

BASE_URL="http://localhost:8080"

echo "==================================="
echo "Testing GraalVM Native Image Example"
echo "==================================="
echo

# Test health endpoint
echo "1. Testing /health endpoint..."
curl -s $BASE_URL/health | jq '.'
echo

# Test root endpoint
echo "2. Testing / endpoint..."
curl -s $BASE_URL/
echo
echo

# Test config endpoint
echo "3. Testing /config endpoint..."
curl -s $BASE_URL/config | jq '.'
echo

# Test user endpoint
echo "4. Testing /user endpoint..."
curl -s -H "X-Tenant-ID: acme-corp" -H "X-User-ID: john@acme.com" \
  $BASE_URL/user/john | jq '.'
echo

# Test session endpoint
echo "5. Testing /session endpoint..."
curl -s -H "X-Tenant-ID: acme-corp" -H "X-User-ID: john@acme.com" \
  $BASE_URL/session | jq '.'
echo

# Test system info endpoint
echo "6. Testing /info endpoint..."
curl -s $BASE_URL/info | jq '.'
echo

echo "==================================="
echo "All tests completed!"
echo "==================================="
