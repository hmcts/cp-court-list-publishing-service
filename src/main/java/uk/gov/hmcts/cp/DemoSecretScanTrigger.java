package uk.gov.hmcts.cp;

/*
 * INTENTIONAL DEMO — synthetic value, NOT a real credential.
 *
 * Purpose: demonstrate that the secrets-scanner GitHub Actions workflow
 * (.github/workflows/secrets-scanner.yml) only runs AFTER a developer pushes
 * the commit to origin. By the time the workflow flags the leak, the blob
 * is already on the remote and reachable via refs/pull/N/head forever.
 *
 * A pre-commit hook running gitleaks (or a husky/git-pre-commit-hook
 * equivalent) on the developer's machine would have caught this BEFORE
 * the push, keeping the secret out of the repo's object database entirely.
 *
 * Do NOT merge this file. Do NOT use this value anywhere.
 */
public final class DemoSecretScanTrigger {

    private static final String apiKey = "Zk3pX9mQ7vR2tN8jL5wH4yC1bF6gD0sA8eU2iO7rT4nM6kP9xV3zB1qY5wE8hJ2";

    private DemoSecretScanTrigger() {
        // utility class — not instantiable
    }
}
