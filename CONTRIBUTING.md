# General notes

Everyone is welcome to contrubute. Maintainers, contributors and users alike are expected to behave in a professional and civil manner.

# Coding guidelines

If this is your first contribution, browse around the code to acquaint yourself with the coding style. We follow common sense and 
functional programming principles. In addition, since you are working on a very minimalistic library, code must be bloat-free and
not obviously non-performant.

Bugfixes and new features must come with tests.

**Avoid ad-hoc abstractions**. The aim of the project is to bring principled RDBMS interaction to the Java programming language. 
This type of work has been long present in other languages - Haskell, Scala and others. Look for inspiration there. Aim for new APIs
to be canonical and follow established mathematical structure and laws.

If in doubt what the above means, act optimistically and send us a pull request, and we will sort out any issues as comments in the PR.

# Running the tests
* `./gradlew clean check` - clean build / test
* `./gradlew clean check -Dci` - to also run findbugs (slower, therefore hidden behind commandline option)

# Publishing

## To local repo
- `./gradlew publishToMavenLocal`

## To artifactory / bintray

### Prerequisites

You need to set the system properties `PUBLISH_USER` and `PUBLISH_KEY` to a valid value in the console

### Snapshot publish
`gradlew artifactoryPublish`

### Release publish
`gradlew bintrayUpload`
