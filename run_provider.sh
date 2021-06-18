#!/bin/bash
./gradlew :run --args="--mode=filter-analysis --fd --ad --fc config/filter_config.json --ac config/analysis_config.json"
