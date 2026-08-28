# Installation

Lexicon is published to GitHub Packages, not Maven Central or the Quarkiverse
registry. GitHub Packages requires authentication for downloads, even on a
public repository, so you need to configure a few things before you can add
the extension as a dependency.

## 1. Create a personal access token

Create a classic personal access token (PAT) with the `read:packages` scope:

1. Go to [GitHub Settings > Developer settings > Personal access tokens](https://github.com/settings/tokens).
2. Generate a new token (classic) with the `read:packages` scope.
3. Copy the token somewhere safe — you'll need it in the next step.

## 2. Configure Maven

Add a server entry to your `~/.m2/settings.xml` that matches the repository
id you'll reference in your project's `pom.xml`. Use your GitHub username and
the PAT you just created:

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_PERSONAL_ACCESS_TOKEN</password>
        </server>
    </servers>
</settings>
```

Avoid committing the token to source control — keep it in `~/.m2/settings.xml`
or supply it via environment variables in CI (`GITHUB_ACTOR` /
`GITHUB_TOKEN` work out of the box on GitHub Actions).

## 3. Add the repository to your project

In your project's `pom.xml`, add the Lexicon GitHub Packages repository. The
`id` must match the server entry from step 2:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/wmeints/lexicon</url>
    </repository>
</repositories>
```

## 4. Add the dependency

```xml
<dependency>
    <groupId>nl.beyondautocomplete</groupId>
    <artifactId>lexicon</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

New versions are published automatically whenever a new tag is pushed for `main`.
