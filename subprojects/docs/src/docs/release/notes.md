The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THIS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
 The list is rendered as is, so use commas after each contributor's name, and a period at the end. 
-->
We would like to thank the following community members for their contributions to this release of Gradle:

[Björn Kautler](https://github.com/Vampire),
[David Marin](https://github.com/dmarin),
[Denis Buzmakov](https://github.com/bacecek),
[Dmitry Pogrebnoy](https://github.com/DmitryPogrebnoy),
[Dzmitry Neviadomski](https://github.com/nevack),
[Eliezer Graber](https://github.com/eygraber),
[Fedor Ihnatkevich](https://github.com/Jeffset),
[Gabriel Rodriguez](https://github.com/gabrielrodriguez2746),
[Herbert von Broeuschmeul](https://github.com/HvB),
[Matthew Haughton](https://github.com/3flex),
[Michael Torres](https://github.com/torresmi),
[Pankaj Kumar](https://github.com/p1729),
[Ricardo Jiang](https://github.com/RicardoJiang),
[Siddardha Bezawada](https://github.com/SidB3),
[Stephen Topley](https://github.com/stopley),
[Vinay Potluri](https://github.com/vinaypotluri).

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features and usability improvements

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

<!--

================== TEMPLATE ==============================

<a name="FILL-IN-KEY-AREA"></a>
### FILL-IN-KEY-AREA improvements

<<<FILL IN CONTEXT FOR KEY AREA>>>
Example:
> The [configuration cache](userguide/configuration_cache.html) improves build performance by caching the result of
> the configuration phase. Using the configuration cache, Gradle can skip the configuration phase entirely when
> nothing that affects the build configuration has changed.

#### FILL-IN-FEATURE
> HIGHLIGHT the usecase or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->

#### PMD and CodeNarc tasks execute in parallel by default
The [PMD](userguide/pmd_plugin.html) and [CodeNarc](userguide/pmd_plugin.html) plugins now use the Gradle worker API and JVM toolchains. These tools now perform analysis via an external worker process and therefore their tasks may now run in parallel within one project.

In Java projects, these tools will use the same version of Java required by the project. In other types of projects, they will use the same version of Java that is used by the Gradle daemon.

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Promoted features in the groovy plugin

- The `GroovyCompileOptions.getDisabledGlobalASTTransformations()` method is now considered stable.

### Promoted features in the Tooling API

- The `GradleConnector.disconnect()` method is now considered stable.

<!--
### Example promoted
-->

### Promoted features in the Eclipse plugin

- The `EclipseClasspath.getContainsTestFixtures()` method is now considered stable.

### Promoted features in the ear plugin

- The `Ear.getAppDirectory()` method is now considered stable.

### Promoted features in the war plugin

- The `War.getWebAppDirectory()` method is now considered stable.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
