
.PHONY: help clean build test validate release-notes deploy build-arm coverage

NAME=`xmllint --xpath "//project/artifactId/text()" pom.xml`
VERSION=`xmllint --xpath "//project/version/text()" pom.xml`
PREVIOUS_TAG=`git tag | sort -r | head -n 1`
CWD=`pwd`
REDIS_VERSION="2.8.19"

# Blue [INFO] prefix before every message, NO_COLOR=1 prints it plain
ifneq ($(NO_COLOR),)
INFO = printf '[INFO] %s\n'
else
INFO = printf '[\033[1;34mINFO\033[m] %s\n'
endif

help:
	@echo "Available targets for $(NAME):"
	@echo "\thelp\t\t\tThis help"
	@echo "\tclean\t\t\tDelete everything in ./target"
	@echo "\tbuild\t\t\tCleans the project and rebuilds the code"
	@echo "\ttest\t\t\tRuns the tests"
	@echo "\tcoverage\t\t\tRun tests and generate a jacoco coverage report"
	@echo "\tvalidate\t\tCheck dependency versions, cves, and licenses"
	@echo "\trelease-notes\t\tCreate release notes for the latest version"
	@echo "\tdeploy\t\t\tClean, build and deploy a version to Github"

clean:
	@$(INFO) "Cleaning"
	@mvn clean

# The tests are turned off because they fail. The problem is that I run a local instance of Redis and
# it prevents the test redis instance from starting up. I would like the tests to use a random available
# port but I haven't fixed them yet.
build:
	@$(INFO) "Building"
	@mvn -DskipTests clean package

test:
	@mvn test

# Runs the tests and writes a coverage report to target/site/jacoco
# Noisy plugin banner lines are silenced through .mvn/maven.config
coverage:
	@$(INFO) "Running tests and generating coverage report"
	@mvn -Dstyle.color=always org.jacoco:jacoco-maven-plugin:0.8.15:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.15:report
	@$(INFO) "Coverage report written to target/site/jacoco/index.html"
	@output=$$(java src/build/CoverageSummary.java target/site/jacoco/jacoco.csv) || exit $$?; \
	printf '%s\n' "$$output" | while IFS= read -r line; do $(INFO) "$$line"; done

validate:
	@mvn versions:display-dependency-updates
	@mvn versions:display-plugin-updates
	@mvn validate -P vulnerabilities
	@mvn validate -P licenses

list-dependencies:
	@mvn dependency:tree

release-notes:
	@$(INFO) "Writing release notes to src/docs/releases/release-$(VERSION).txt"
	@echo "$(VERSION)" >> src/docs/releases/release-$(VERSION).txt
	@echo "" >> src/docs/releases/release-$(VERSION).txt
	@git log --pretty="%s" $(PREVIOUS_TAG)... master >> src/docs/releases/release-$(VERSION).txt

deploy: build
	@$(INFO) "Uploading to maven central"
	@mvn clean deploy -P release
	@$(INFO) "Creating github release"
	@gh release create $(NAME)-$(VERSION) target/$(NAME)-$(VERSION).jar target/$(NAME)-$(VERSION)-javadoc.jar target/$(NAME)-$(VERSION)-sources.jar -F src/docs/releases/release-$(VERSION).txt
	@$(INFO) "Tagging and pushing to github"
	@git tag $(NAME)-$(VERSION)
	@git push && git push --tags

build-arm:
	@$(INFO) "Configuring build environment"
	# Testing has revealed that a QEMU generated binary differs from a binary generated on ARM hardware
	# Redis tests run using `make tests` inside the docker image will fail with the QEMU generated binary
	# see https://github.com/codemonstur/embedded-redis/pull/2 for details
	# Uncomment this line if you are not running on ARM hardware:
	#	@docker run -it --rm --privileged multiarch/qemu-user-static --credential yes --persistent yes
	@docker build -t embedded-redis/ubuntu-arm -f src/main/binaries/DockerArm src/main/binaries
	@-mkdir target > /dev/null
	@$(INFO) "Downloading Redis sources for $(REDIS_VERSION)"
	@cd target && wget https://download.redis.io/releases/redis-$(REDIS_VERSION).tar.gz > /dev/null
	@$(INFO) "Unpacking Redis sources"
	@cd target && tar xfvz redis-$(REDIS_VERSION).tar.gz > /dev/null
	@$(INFO) "Compiling Redis"
	@docker run -it --rm -v $(CWD)/target/redis-$(REDIS_VERSION):/redis embedded-redis/ubuntu-arm
	@$(INFO) "Done. Access your binary at target/redis-$(REDIS_VERSION)/src/redis-server"
