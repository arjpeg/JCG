#!/bin/bash
set -e  # stop if any command fails

JCG_IMAGE="jcg_dev"
COMPARECG_CONTAINER="compare_cg_dev"
JCG_DIR="/home/mohammad/projects/cgFuzz/JCG"
SCG_DIR="/home/mohammad/projects/cgFuzz/dev/scg"
DCG_DIR="/home/mohammad/projects/cgFuzz/dev/dcg"
BOUND_DIR="/home/mohammad/projects/cgFuzz/dev/boundaries"

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
  --mount type=bind,source="$SCG_DIR",target="/scg",readonly \
  --mount type=bind,source="$DCG_DIR",target="/dcg",readonly \
  --mount type=bind,source="$BOUND_DIR",target="/boundaries" \
  --mount type=bind,source="$JCG_DIR",target="/home/JCG/JCG" \
  "$JCG_IMAGE" \
  bash -c "
    cd /home/JCG/JCG && \
    sbt compile && \
    sbt \"; project jcg_evaluation; runMain CompareCGs \
      --input1 /dcg/cg.json \
      --input2 /scg/cg.zip \
      --output /boundaries \
      --showPrecisionRecall all \
      --showBoundaries \
      --nonStrict\" 
  "
