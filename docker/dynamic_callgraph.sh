#!/bin/bash
set -e  # stop if any command fails

PROJECT="axion"
JCG_IMAGE="jcg_dev"
DYCG_CONTAINER="${PROJECT}_dynamic_cg_dev"

JCG_DIR="/home/mohammad/projects/cgFuzz/code/analyses/call_graph/JCG"
DCG_DIR="/home/mohammad/projects/cgFuzz/data/analyses/call_graph/$PROJECT/dyncg/atl_jazzer/5h"
REPO_DIR="/home/mohammad/projects/cgFuzz/data/projects/$PROJECT"
CORPUS_DIR="/home/mohammad/projects/cgFuzz/data/corpus/$PROJECT/atl_jazzer/5h"

# Get host user and group IDs
HOST_UID=$(id -u)
HOST_GID=$(id -g)

# Build JCG image with UID/GID args
docker build --tag "$JCG_IMAGE" \
  --build-arg UID="$HOST_UID" \
  --build-arg GID="$HOST_GID" \
  .

# Remove any old container
docker rm -f "$DYCG_CONTAINER" || true

# Run the container
docker run -it --name "$DYCG_CONTAINER" \
  --mount type=bind,source="$DCG_DIR",target="/dcg" \
  --mount type=bind,source="$JCG_DIR",target="/home/JCG/JCG" \
  --mount=type=bind,source="$REPO_DIR/repo",target="/repo",readonly \
  --mount=type=bind,source="$CORPUS_DIR",target="/corpus",readonly \
  --mount=type=bind,source="$REPO_DIR/jcg.conf",target="/jcg-conf/$PROJECT.conf",readonly \
  "$JCG_IMAGE" \
  bash -c "
    cd /home/JCG/JCG && \
    sbt compile && \
    sbt \"; project jcg_evaluation; runMain Evaluation \
    --input /jcg-conf/ \
    --output /dcg \
    --program-args /corpus \
    --adapter Dynamic \
    --algorithm-prefix Dynamic \
    --project-prefix $PROJECT \
    --debug\"
  "
