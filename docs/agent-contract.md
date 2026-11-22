# Agent contract

## Purpose and layout

This repository contains the MuteDeck webhook receiver for Hubitat Elevation. The Hubitat Groovy app is under `apps/`; `packageManifest.json` describes the Hubitat Package Manager package, and `readme.md` documents installation and configuration.

## Commands and validation

There is no local build or automated test command. Validate changes by reviewing Groovy syntax and the package manifest. Installation, OAuth configuration, and webhook behavior require a Hubitat hub and are not routine validation steps.

## Safety boundaries

- Do not call a live Hubitat hub, configure a real webhook, or change devices during validation.
- Do not expose generated webhook URLs, OAuth credentials, hub addresses, or live device details in source or logs.
- Preserve the documented default that cloud endpoint access is disabled unless a user enables it.

## Completion evidence

State whether validation was source review only or included an explicitly authorized test hub. Do not infer live hub behavior from static checks.
