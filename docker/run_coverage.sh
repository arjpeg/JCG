#!/bin/bash
set -e  # stop if any command fails

PROJECT="axion"
JCG_IMAGE="jcg_dev"
COMPARECG_CONTAINER="${PROJECT}_compare_cg"

JCG_DIR="/home/mohammad/projects/cgFuzz/code/analyses/call_graph/JCG"
DCG_DIR="/home/mohammad/projects/cgFuzz/data/analyses/call_graph/$PROJECT/dyncg/jazzer/24h"
BOUND_DIR="/home/mohammad/projects/cgFuzz/data/analyses/call_graph/$PROJECT/boundaries/in_scg/WALA_0-cfa_jazzer_24h"

# Get host user and group IDs
HOST_UID=$(id -u)
HOST_GID=$(id -g)

# Build JCG image with UID/GID args
docker build --tag "$JCG_IMAGE" \
  --build-arg UID="$HOST_UID" \
  --build-arg GID="$HOST_GID" \
  .

# Remove any old container
docker rm -f "$COMPARECG_CONTAINER" || true

# Run the container
docker run -it --name "$COMPARECG_CONTAINER" \
  --mount type=bind,source="$DCG_DIR",target="/dcg",readonly \
  --mount type=bind,source="$BOUND_DIR",target="/boundaries" \
  --mount type=bind,source="$JCG_DIR",target="/home/JCG/JCG" \
  "$JCG_IMAGE" \
  bash -c "
    cd /home/JCG/JCG && \
    sbt compile && \
    sbt -J-Xmx28g \
     \"; project jcg_evaluation; runMain CalculateBoundaryCoverage \
      --dynamic-cg /dcg/cg_v1.json \
    --boundary-methods /boundaries/boundaries.json \
    --output-json /boundaries/boundary_coverage.json \
    --output-report /boundaries/coverage_report.txt\" 
  "
