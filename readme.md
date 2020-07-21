
## Overview

Spring boot and dockerised version of HAPI JPA FHIR Server.

## Currently using 

postgres 11 with db fhircdr

elastic 6.8 (see instructions below to install pluggin)

java 11

maven


## Elastic Instructions

mac
sudo bin/plugin install analysis-phonetic

windows 
.\elasticsearch-plugin install analysis-phonetic

## project build

mvn clean install

## docker build

docker build . -t wildfyre-cdr

docker tag wildfyre-cdr thorlogic/wildfyre-cdr

docker push thorlogic/wildfyre-cdr





