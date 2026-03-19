# Docker assets

## Integration compose and the Azurite “account key”

`docker-compose.integration.yml` sets a default `AZURE_STORAGE_ACCOUNT_KEY` that matches the **public, Microsoft-documented** default for the [Azurite](https://github.com/Azure/Azurite) blob emulator account `devstoreaccount1`.

- It is **not** a production secret and is **the same for every project** using the default emulator setup.
- Automated secret scanners (e.g. Gitleaks) may still report it because it **looks like** a high-entropy key; the repository marks that line with `# gitleaks:allow` and keeps this note for reviewers.
- To use **real** Azure Storage in integration runs, override `AZURE_STORAGE_ACCOUNT_KEY` (and related settings) via environment; do not use the Azurite default for non-emulator accounts.
