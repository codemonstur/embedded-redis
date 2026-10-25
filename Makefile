.DEFAULT_GOAL := help
.PHONY: help clean build test coverage validate release-notes deploy build-arm

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

help: ## Show this help
	@grep -hE '^[a-zA-Z0-9_.-]+:' $(MAKEFILE_LIST) | grep -vE '^[a-zA-Z0-9_.-]+:=' | \
		awk 'BEGIN {FS = ":"} /^\./ {next} {name = $$1; sub(/[[:blank:]]+$$/, "", name); doc = $$0; sub(/^[^#]*## /, "", doc); if (doc == $$0) sub(/^[^:]*:[[:blank:]]*/, "", doc); printf "  \033[36m%-10s\033[0m %s\n", name, doc}'

clean: ## Delete everything in ./target
	@$(INFO) "Cleaning"
	@mvn clean

build: ## Cleans the project and rebuilds the code
	@$(INFO) "Building"
	@mvn -DskipTests clean package

test: ## Runs the tests
	@mvn test

coverage: ## Run tests and generate a jacoco coverage report
	@$(INFO) "Running tests and generating coverage report"
	@mvn -Dstyle.color=always org.jacoco:jacoco-maven-plugin:0.8.15:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.15:report
	@$(INFO) "Coverage report written to target/site/jacoco/index.html"
	@output=$$(java src/build/CoverageSummary.java target/site/jacoco/jacoco.csv) || exit $$?; \
	printf '%s\n' "$$output" | while IFS= read -r line; do $(INFO) "$$line"; done

validate: ## Check dependency versions, cves, licenses, and code quality
	@mvn versions:display-dependency-updates
	@mvn versions:display-plugin-updates
	@mvn validate -P vulnerabilities
	@mvn validate -P licenses
	@mvn validate -P codequality

release-notes: ## Write release notes in src/docs/releases for the current version
	@mkdir -p src/docs/releases
	@$(INFO) "Writing release notes to src/docs/releases/release-$(VERSION).txt"
	@echo "$(VERSION)" >> src/docs/releases/release-$(VERSION).txt
	@echo "" >> src/docs/releases/release-$(VERSION).txt
	@git log --pretty="%s" $(PREVIOUS_TAG)... master >> src/docs/releases/release-$(VERSION).txt

deploy: ## Clean, build and deploy a version to Github and Maven Central
	@$(INFO) "Uploading to maven central"
	@mvn clean deploy
	@$(INFO) "Creating github release"
	@gh release create $(NAME)-$(VERSION) target/$(NAME)-$(VERSION).jar target/$(NAME)-$(VERSION)-javadoc.jar target/$(NAME)-$(VERSION)-sources.jar -F src/docs/releases/release-$(VERSION).txt
	@$(INFO) "Tagging and pushing to github"
	@git tag $(NAME)-$(VERSION)
	@git push && git push --tags

build-arm: ## Build a binary that might work for ARM architectures
	@$(INFO) "Configuring build environment"
	# Testing has revealed that a QEMU generated binary differs from a binary generated on ARM hardware
	# Redis tests run using `make tests` inside the docker image will fail with the QEMU generated binary
	# see https://github.com/codemonstur/embedded-redis/pull/2 for details
	# Uncomment this line if you are not running on ARM hardware:
	#	@docker run -it --rm --privileged multiarch/qemu-user-static --credential yes --persistent yes
	@docker build -t embedded-redis/ubuntu-arm -f src/docs/compile/DockerArm src/docs/compile
	@-mkdir target > /dev/null
	@$(INFO) "Downloading Redis sources for $(REDIS_VERSION)"
	@cd target && wget https://download.redis.io/releases/redis-$(REDIS_VERSION).tar.gz > /dev/null
	@$(INFO) "Unpacking Redis sources"
	@cd target && tar xfvz redis-$(REDIS_VERSION).tar.gz > /dev/null
	@$(INFO) "Compiling Redis"
	@docker run -it --rm -v $(CWD)/target/redis-$(REDIS_VERSION):/redis embedded-redis/ubuntu-arm
	@$(INFO) "Done. Access your binary at target/redis-$(REDIS_VERSION)/src/redis-server"
