# Changelog
All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Changed
- Java 21 readiness (programme-level work, delivered across several work packages):
  - The supported floor is now **JDK 11**, with **JDK 21** as the primary target and the version the project is tested on.
  - Jarviz CLI performs a JDK and Node version preflight before running a command, failing with an actionable message on unsupported versions. Version parsing handles both legacy (`1.8.0_292`) and modern (`21.0.4`) JDK version strings.
  - Documentation states the supported JDK/Node ranges, and the Java 8-only JVMS/opcode references were replaced with the current (SE 21) specification links.
  - Known limitation: `jarviz-graph` is still on webpack 4 and does not build on modern Node (its build fails on Node 20); Node 14+ is supported for running the CLI, but building the graph from source still needs an older Node toolchain. Modernizing that build is tracked as separate work.
- The CLI version and the artifact/npm version resolution logic are unchanged by this work.

## [0.1.7] - 2021-05-06
### Changed
- Fixed the filter logic issue associated with multiple exclusions (by @nath-abhishek).

## [0.1.6]
- Skipped due to a build issue with Nexus

## [0.1.5] - 2021-04-01
### Changed
- Contributions by @hadisfr
  - Adding Java 14 support.
  - Showing stacktrace in error messages.
- Moved manual release script into build-config.
- Documentation updates.

## [0.1.4] - 2021-03-31
### Changed
- Contributions by @nath-abhishek 
  - New `continueOnMavenError` flag in `config.json`. When set to `true`, `Jarvis` will continue processing remaining applications, from `artifact.json`, after it encounters maven errors. Default value is `false`.
  - Added capability to use `RELEASE` and `LATEST` as dependency versions in `artifact.json`.
  - `appSetName` is now written to output `jsonl` file.  
- New `mavenTimeOutSeconds` flag in `config.json` to set time out for mvn process to prevent hanging indefinitely (default 5 min).
- Added `jarviz-cli/samples/sample_jarviz_result.jsonl`.
- Upgraded immutables to 2.8.2
- Upgraded ASM to 9.1
- Upgraded Guava to 30.0-jre
- Upgraded JUnit to 4.13.1
- Added Github Actions scripts for building and releasing.
- Removed Travis build scripts.

## [0.1.3] - 2020-03-12
### Fixed
- Fixed MavenArtifactDiscoveryServiceTest.

## [0.1.2] - 2020-03-12
### Fixed
- Fixing release script to remove NPM version prefix.

## [0.1.1] - 2020-03-12
### Changed
- Release script was moved to top.
- NPM_TOKEN is not needed in .npmrc file.

## [0.1.0] - 2020-03-12
### Changed
- Released Jarviz CLI.

## [0.0.1] - 2020-03-05
### Added
- Open sourcing the initial version of Jarviz dependency analyser.
