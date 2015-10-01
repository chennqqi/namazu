#!/bin/bash

set -e # exit on an error
. ${EQ_MATERIALS_DIR}/lib.sh

CHECK_PREREQUISITES
FETCH_ETCD
BUILD_DOCKER_IMAGE

exit 0
