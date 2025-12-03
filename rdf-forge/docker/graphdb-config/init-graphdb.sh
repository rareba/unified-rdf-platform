#!/bin/bash
# GraphDB Repository Initialization Script
# Creates the rdf-forge repository if it doesn't exist

GRAPHDB_URL="${GRAPHDB_URL:-http://graphdb:7200}"
REPO_ID="rdf-forge"
MAX_RETRIES=30
RETRY_DELAY=10

echo "Waiting for GraphDB to be ready..."

# Wait for GraphDB to be available
for i in $(seq 1 $MAX_RETRIES); do
    if curl -sf "${GRAPHDB_URL}/rest/repositories" > /dev/null 2>&1; then
        echo "GraphDB is ready!"
        break
    fi
    echo "Attempt $i/$MAX_RETRIES: GraphDB not ready, waiting ${RETRY_DELAY}s..."
    sleep $RETRY_DELAY
done

# Check if repository already exists
echo "Checking if repository '$REPO_ID' exists..."
REPOS=$(curl -sf "${GRAPHDB_URL}/rest/repositories")
if echo "$REPOS" | grep -q "\"id\":\"$REPO_ID\""; then
    echo "Repository '$REPO_ID' already exists. Skipping creation."
    exit 0
fi

# Create repository using the config file
echo "Creating repository '$REPO_ID'..."
curl -X POST \
    -H "Content-Type: multipart/form-data" \
    -F "config=@/config/rdf-forge-repo-config.ttl" \
    "${GRAPHDB_URL}/rest/repositories"

if [ $? -eq 0 ]; then
    echo "Repository '$REPO_ID' created successfully!"
else
    echo "Failed to create repository '$REPO_ID'"
    exit 1
fi

# Load sample RDF data (optional)
if [ -f "/config/demo-data.ttl" ]; then
    echo "Loading demo RDF data..."
    curl -X POST \
        -H "Content-Type: application/x-turtle" \
        --data-binary "@/config/demo-data.ttl" \
        "${GRAPHDB_URL}/repositories/${REPO_ID}/statements?context=<https://example.org/graph/demo>"
    echo "Demo RDF data loaded!"
fi

echo "GraphDB initialization complete!"
