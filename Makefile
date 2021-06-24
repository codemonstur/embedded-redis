
.PHONY: help clean build check-versions release-notes deploy build-arm

DATE=`date +'%F'`
NAME=`xmllint --xpath "//project/artifactId/text()" pom.xml`
VERSION=`xmllint --xpath "//project/version/text()" pom.xml`
PREVIOUS_TAG=`git tag | sort -r | head -n 1`
CWD=`pwd`
REDIS_VERSION="2.8.19"

help:
	@echo "Available targets for $(NAME):"
	@echo "\thelp\t\t\tThis help"
	@echo "\tclean\t\t\tDelete everything in ./target"
	@echo "\tbuild\t\t\tCleans the project and rebuilds the code"
	@echo "\tcheck-versions\t\tCheck if the versions of dependencies are up to date"
	@echo "\trelease-notes\t\tCreate release notes for the latest version"
	@echo "\tdeploy\t\t\tClean, build and deploy a version to Github"

clean:
	@echo "[$(NAME)] Cleaning"
	@mvn -Dorg.slf4j.simpleLogger.defaultLogLevel=warn clean

build:
	@echo "[$(NAME)] Building"
	@mvn -Dorg.slf4j.simpleLogger.defaultLogLevel=warn -DskipTests=true clean package

check-versions:
	@mvn versions:display-dependency-updates
	@mvn versions:display-plugin-updates

list-dependencies:
	@mvn dependency:tree

release-notes:
	@echo "[$(NAME)] Writing release notes to src/docs/releases/release-$(VERSION).txt"
	@echo "$(VERSION)" > src/docs/releases/release-$(VERSION).txt
	@echo "" >> src/docs/releases/release-$(VERSION).txt
	@git log --pretty="%s" $(PREVIOUS_TAG)... master >> src/docs/releases/release-$(VERSION).txt

deploy: build
	@echo "[$(NAME)] Creating github release"
	@hub release create -a target/$(NAME)-$(VERSION).jar -a target/$(NAME)-$(VERSION)-javadoc.jar -a target/$(NAME)-$(VERSION)-sources.jar -F src/docs/releases/release-$(VERSION).txt $(NAME)-$(VERSION)
	@echo "[$(NAME)] Uploading to maven central"
	@mvn clean deploy -P release
	@echo "[$(NAME)] Tagging and pushing to github"
	@git tag $(NAME)-$(VERSION)
	@git push && git push --tags

build-arm:
	@echo "[$(NAME)] Configuring build environment"
	# Testing has revealed that a QEMU generated binary differs from a binary generated on ARM hardware
	# Redis tests run using `make tests` inside the docker image will fail with the QEMU generated binary
	# see https://github.com/codemonstur/embedded-redis/pull/2 for details
	# Uncomment this line if you are not running on ARM hardware:
	#	@docker run -it --rm --privileged multiarch/qemu-user-static --credential yes --persistent yes
	@docker build -t embedded-redis/ubuntu-arm -f src/main/binaries/DockerArm src/main/binaries
	@-mkdir target > /dev/null
	@echo "[$(NAME)] Downloading Redis sources for $(REDIS_VERSION)"
	@cd target && wget https://download.redis.io/releases/redis-$(REDIS_VERSION).tar.gz > /dev/null
	@echo "[$(NAME)] Unpacking Redis sources"
	@cd target && tar xfvz redis-$(REDIS_VERSION).tar.gz > /dev/null
	@echo "[$(NAME)] Compiling Redis"
	@docker run -it --rm -v $(CWD)/target/redis-$(REDIS_VERSION):/redis embedded-redis/ubuntu-arm
	@echo "[$(NAME)] Done. Access your binary at target/redis-$(REDIS_VERSION)/src/redis-server"
