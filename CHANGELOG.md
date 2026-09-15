# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](http://semver.org/).

The format is based on [Keep a Changelog](http://keepachangelog.com/).

## Unreleased

### Changed

- Generated Integration Dependency now groups consumed events into event resources by their `@OrdId` CDS annotation (previously all consumed events were bundled into a single fixed `RawEvent` event resource). Only events that are both consumed and annotated with `@OrdId` are included.

## Version 4.1.0 - 2026-03-09

### Added

- Support emitting events for Event Hub Connectivity Plan
- Support for CAP Transactional Event Queues

### Changed

- Improve logging during Event Hub Messaging Service initialization

### Fixed

### Removed

## Version 4.0.1 - 2025-05-20

### Added

- Preparations for compatibility with CAP Java 4

## Version 4.0.0 - 2025-03-11

### Added

- Initial release of OS plugin
