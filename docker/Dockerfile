FROM ubuntu:20.04

# Set user and group
ARG USER=JCG
ARG GROUP=users
ARG UID=1000
ARG GID=100

# Delete user and group to ensure they do not exist
RUN OLD_USER=$(awk -F\: "\$3 == ${UID} {print \$1}" /etc/passwd); if [ ! -z "$OLD_USER" ]; then echo "delete user $OLD_USER"; deluser $OLD_USER; fi
RUN OLD_GROUP=$(awk -F\: "\$3 == ${GID} {print \$1}" /etc/group); if [ ! -z "$OLD_GROUP" ]; then echo "delete group $OLD_GROUP"; delgroup $OLD_GROUP; fi
RUN delgroup $GROUP
# Add user and group
RUN addgroup --gid ${GID} ${GROUP}
RUN adduser --force-badname --uid ${UID} --gid ${GID} --shell /bin/sh ${USER}

##
# INSTALL PRELIMINARY TOOLS
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive \
    apt-get -y install sudo apt-transport-https ca-certificates software-properties-common apt-utils  \
                       git curl less nano vim zip zlib1g-dev wget python3.5 unzip aptitude make gcc g++ \
                       libtool libncurses5-dev libopenmpi-dev openmpi-bin libsqlite3-dev libffi-dev mcpp \
                       flex bison clang build-essential

# Disable sudo password prompt for all users
RUN echo "${USER} ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers
# Add user to sudo group
RUN adduser ${USER} sudo

USER ${UID}:${GID}

# Install OpenJDK 8 version 342
ARG JDK_DIR="/home/${USER}/jdk"
RUN sudo apt-get -y autoremove openjdk* && \
    sudo apt-get -y purge openjdk* && \
    mkdir ${JDK_DIR} && \
    cd ${JDK_DIR} && \
    wget https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u342-b07/OpenJDK8U-jdk_x64_linux_hotspot_8u342b07.tar.gz && \
    sudo tar xvf $JDK_DIR/OpenJDK8U-jdk_x64_linux_hotspot_8u342b07.tar.gz -C /opt && \
    sudo update-alternatives --install /usr/bin/java java /opt/jdk8u342-b07/bin/java 1000 && \
    sudo update-alternatives --install /usr/bin/javac javac /opt/jdk8u342-b07/bin/javac 1000 && \
    sudo update-alternatives --install /usr/bin/jar jar /opt/jdk8u342-b07/bin/jar 1000

## install sbt
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list && \
    sudo apt-key adv --keyserver hkps://keyserver.ubuntu.com:443 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 && \
    sudo apt-get update && \
    sudo apt-get install sbt


## install OPAL
ARG OPAL_BRANCH="develop"
ARG OPAL_COMMIT_ID="fa37a36181cdcb3135999e76ffe90e617a434f8e"
ARG OPAL_DIR="/home/$USER/OPAL"
RUN git clone https://github.com/stg-tud/opal.git ${OPAL_DIR} && \
    cd ${OPAL_DIR} && \
    git checkout ${OPAL_BRANCH} && \
    git reset --hard ${OPAL_COMMIT_ID} && \
    sbt publishLocal

## INSTALL SOUFFLE
ARG SOUFFLE_CC="gcc"
ARG SOUFFLE_CXX="g++"
ARG SOUFFLE_CONFIGURE_OPTIONS=""
ARG SOUFFLE_GITHUB_USER="souffle-lang"
ARG SOUFFLE_GIT_BRANCH="master"
ARG SOUFFLE_GIT_REVISION="90a2d58455d829c64ffe0fef9074de969697e3c8"
ARG SOUFFLE_MAKE_JOBS="2"
ARG SOUFFLE_DIR="/home/$USER/souffle"

ENV CC "${SOUFFLE_CC}"
ENV CXX "${SOUFFLE_CXX}"

RUN git clone https://github.com/${SOUFFLE_GITHUB_USER}/souffle $SOUFFLE_DIR && \
    cd $SOUFFLE_DIR && \
    git checkout ${SOUFFLE_GIT_BRANCH} && \
    git reset --hard ${SOUFFLE_GIT_REVISION} && \
    git clean -xdf && \
    ./bootstrap && \
    ./configure ${SOUFFLE_CONFIGURE_OPTIONS} && \
    make -j${SOUFFLE_MAKE_JOBS} && \
    ./src/souffle && \
    sudo make install -j${SOUFFLE_MAKE_JOBS} && \
    souffle -h

## INSTALL DOOP
ARG DOOP_COMMIT_ID="2df4dd3856ca721bb0c74e18d09d81731df60062"
ARG DOOP_BRANCH="master"
ARG DOOP_DIR="/home/$USER/doop"
ENV DOOP_HOME="$DOOP_DIR"
RUN git clone https://github.com/plast-lab/doop.git $DOOP_DIR && \
    cd $DOOP_DIR && \
    git checkout ${DOOP_BRANCH} && \
    git reset --hard ${DOOP_COMMIT_ID} && \
    ./doop -h


RUN mkdir -p /home/JCG/JCG

COPY jre.conf /home/JCG/JCG

WORKDIR /home/JCG/JCG


