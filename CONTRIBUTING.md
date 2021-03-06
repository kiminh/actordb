# Contributing

Take a look at the open issues and pull requests to see open tasks.
You are more than welcome to contribute any PR regardless if it's listed or not.

## Workflow

- the `master`-branch is protected, so no commits can be pushed directly to it
- please create a new PR for any proposed changes
- **use the issue and PR templates**
  - [Create a bug report](https://github.com/CodeLionX/actordb/issues/new?template=bug_report_template.md)
  - [Ask a question or file an issue](https://github.com/CodeLionX/actordb/issues/new?template=issue_template.md)
- each branch must have one of the following prefixes:
  - `fix/` for bug fixes and small, urgent changes
  - `feature/` for new feature proposals
  - `doc/` for changes that only affect documentation files and **no source code**
  - `refactor/` for refactorings that do not introduce new features or just few
- when starting work on this project please use the [labels](https://github.com/CodeLionX/actordb/labels) to mark the state of your PR
  - start with label `WIP` (work in progress)
  - when you are finished with the PR, mark it ready for review with `Needs Review`
  - if the reviewer approves your PR, he/she can mark it with `Merge` or merge it into master directly
  - when your PR is marked with `Merge`, any contributer is allowed to merge the PR into master
  
  

## Set up development environment (Linux)

### Use commandline tools
- Install JDK, eg. JDK-1.8:

  ```sh
  sudo apt install openjdk-8-jdk
  ```

- Install `sbt`, see [Download SBT](https://www.scala-sbt.org/download.html):

  ```sh
  echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
  sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
  sudo apt-get update
  sudo apt-get install sbt
  ```


### Use IDE, eg. Intellij IDEA

- Install JDK, eg. JDK-1.8:

  ```sh
  sudo apt install openjdk-8-jdk
  ```

- [Download](https://www.jetbrains.com/idea/download/#section=linux) and install Intellij IDEA
- Install _Scala_ and _SBT_ plugins
- Import project _as an SBT project_ (only available after installing the _SBT_ plugin), see screenshot below
  
  ![Import Project from SBT](./doc/images/import_project.png)
