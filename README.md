# JaCoCo to Gerrit Maven plugin

This maven plugin collects test line coverage for files changed in a particular Gerrit changset and uploads the results to Gerrit. However, following tweaks need to be in place
  
  - Gerrit fork <https://github.com/stepanv/gerrit/tree/v2.12-coverage-css> which adds CSSs support on top of [another fork of Gerrit](https://github.com/muryoh/gerrit/tree/v2.12-coverage)
    which adds functionality to mark lines as [Code Mirror gutters](https://codemirror.net/demo/marker.html) to show the test code coverage in side-by-side view.
  - Gerrit plugin <https://github.com/Ullink/gerrit-coverage-plugin> that provides REST api for uploading test code coverage and 
    getting a coverage for a particular file
    
## Usage

In Gerrit, the user (denoted by *gerrit.user* bellow) needs to have enabled HTTP access: *Settings* -> *HTTP Password* -> *Generate HTTP password*.

To run this maven plugin

 1. at first, execute `mvn test` (or `mvn verify`) with enabled JaCoCo probes (see next chapter to get hints how to do that), which creates 
    *jacoco.exec* file with coverage data. This data file is read by *this plugin* and the collected information is uploaded to Gerrit.
 2. to run this plugin:

    - if executed as a Jenkins/Hudson job with enabled [Gerrit Trigger](https://wiki.jenkins-ci.org/display/JENKINS/Gerrit+Trigger) (if the build is triggered
      by an [Gerrit Event](https://wiki.jenkins-ci.org/display/JENKINS/Gerrit+Trigger#GerritTrigger-TriggerConfiguration), several system environment variables 
      (prefixed with `GERRIT_*`) are set and used by this plugin)

            $ net.uvavru.maven.plugin:jacoco-gerrit-maven-plugin:jacoco-to-gerrit -Djacoco.execFile=target/jacoco.exec -Dgerrit.password=***** -Dgerrit.port=8080 -Dgerrit.username=robot -Dgerrit.host=gerrit.someorg.com
 
    - otherwise additional parameters must be set explicitly:
   
            $ ... -Dgerrit.projectName=gerrit-project-name -Dgerrit.branchName=master -Dgerrit.changeId=I156abb8bf7d9bbb151ebfe130bb4568320c3252b -Dgerrit.revisionId=d80c5bffddcebc7d88baf57ad4cc3da42a9692e3
   
        these parameters are configurable by system environment variables as well: `GERRIT_PROJECT`, `GERRIT_BRANCH`, `GERRIT_CHANGE_ID`, `GERRIT_PATCHSET_REVISION` (which are automatically set by Jenkins/Hudson if Gerrit Trigger is used).

### Running tests with JaCoCo probes

To run maven tests with JaCoCo probes, jacoco agent has to be enabled in the JVM running the tests. For further information, 
refer to [Jacoco plugin site](http://eclemma.org/jacoco/trunk/doc/prepare-agent-mojo.html).
 
 1. Configure `org.jacoco:jacoco-maven-plugin` to generate JVM agent property or to set *surefire* or *failsafe* `argLine` directly
 2. Configure the location where to write Jacoco probes - the *jacoco.exec* file
 3. Configure `maven-surefire-plugin` (or `maven-failsafe-plugin`) to use the enhanced JVM parameters
 4. Execute `test` (or `verify`) maven phase
 
        $ mvn clean test
      
    or
    
        $ mvn clean verify
 
 5. Now, when the *jacoco.exec* file is generated, this plugin can be run in order to collect relevant information and upload it to Gerrit.

As a concrete example of a configuration of Jacoco and test execution in Maven, one can consider looking at 
[Jersey sources](https://github.com/jersey/jersey/commit/afb3a3a7788b5be3cc71d180b0066c512d9f6d92).

## Notes
This maven plugin is a substitution for <https://github.com/muryoh/sonar-gerrit-plugin> which is capable of the same functionality
(i.e., to upload test code coverage to Gerrit). However, in that case, Sonarqube is required and the Sonar analysis has to be executed.
