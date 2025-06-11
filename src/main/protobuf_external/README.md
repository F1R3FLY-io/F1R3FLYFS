# External Protobuf Files

This directory contains protobuf files copied from external F1r3fly dependencies to make the project self-contained for CI/CD.

## Origin
Originally sourced from: `../f1r3fly/node/target/protobuf_external`

## Contents
- **google/protobuf/**: Standard Google Protocol Buffer definitions
- **scalapb/**: ScalaPB-specific protobuf extensions

These files were copied to eliminate external path dependencies during build processes. 