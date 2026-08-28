# Releasing a new version

Releases are cut manually and deliberately, by pushing a version tag. Merging
a pull request into `main` never publishes anything by itself — publishing
only happens when a tag matching `v*.*.*` is pushed.

## Release checklist

1. Make sure `main` is up to date and contains everything you want in the
   release.
2. Set the release version in every module's `pom.xml` (the version currently
   reads `0.1.0-SNAPSHOT` in each module):

   ```shell
   mvn versions:set -DnewVersion=0.2.0
   mvn versions:commit
   ```

3. Commit the version bump and open a PR into `main` (this goes through the
   normal PR validation workflow, so compilation and tests are checked before
   merging).
4. After the PR is merged, tag the resulting commit on `main` and push the
   tag:

   ```shell
   git checkout main
   git pull
   git tag v0.2.0
   git push origin v0.2.0
   ```

   Pushing the tag triggers the publish workflow, which verifies the tag
   version matches the `pom.xml` version and then runs `mvn deploy` to
   publish `lexicon` and `lexicon-deployment` to GitHub Packages.
5. Bump the version back to the next `-SNAPSHOT` on `main` (e.g.
   `0.3.0-SNAPSHOT`) via the same `versions:set` + PR flow, so ongoing work
   isn't accidentally built against a released version number.

## Why tags instead of every push to `main`

Tying publishing to a tag push — rather than every push to `main` — means a
routine merge (a doc fix, a refactor, a dependency bump) never triggers an
accidental release. Creating and pushing the tag is the one deliberate action
that cuts a version, so you always know exactly when and what got published.
