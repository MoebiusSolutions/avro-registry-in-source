Publishing a Release
================

This is pretty ugly at the moment. At some point I'll get the build server setup with
GitHub, Bintray, and Maven Central credentials.

Tagging the Release
----

Verify no local changes:

	git status

Tag/push release to source:

	VERSION=1.6

	mvn --batch-mode versions:set -DgenerateBackupPoms=false -DnewVersion="${VERSION}" && \
	  git add :/ && \
	  git commit -m "Rolling version to ${VERSION}" && \
	  git tag "release/avro-registry-in-source-${VERSION}" && \
	  git push origin master && \
	  git push origin "release/avro-registry-in-source-${VERSION}"

	VERSION=1.7-SNAPSHOT

	mvn --batch-mode versions:set -DgenerateBackupPoms=false -DnewVersion="${VERSION}" && \
	  git add :/ && \
	  git commit -m "Rolling version to ${VERSION}" && \
	  git push origin master

Wait for release build to finish, then download the bundle.

	https://jenkins.moesol.com/job/avro-registry-in-source_release/

Create a new version in Bintray, uploading the contents of the bundle.

Publish to Maven Central from within Bintray.

