# Atomist 'rug-cli'

[![Build Status](https://travis-ci.org/atomist/rug-cli.svg?branch=master)](https://travis-ci.org/atomist/rug-cli)
[![Slack Status](https://join.atomist.com/badge.svg)](https://join.atomist.com/)

Atomist Rug command-line interface for creating and running rugs.

See the [Rug CLI Quick Start][quick] and [Rug CLI documentation][doc]
for more detailed information.

[quick]: http://docs.atomist.com/quick-starts/rug-cli/
[doc]: http://docs.atomist.com/rug/rug-cli/

## Installation

See [Rug CLI Installation][install] for installation instructions
or on how to run the CLI in a Docker container.

[install]: http://docs.atomist.com/rug/rug-cli/rug-cli-install/

## Support

General support questions should be discussed in the `#support`
channel on our community slack team
at [atomist-community.slack.com](https://join.atomist.com).

If you find a problem, please create an [issue][].

[issue]: https://github.com/atomist/rug-cli/issues

## Development

You can build, test, and install the project locally with [maven][].

[maven]: https://maven.apache.org/

```sh
$ mvn install
```

To create a new release of the project, simply push a tag of the form
`M.N.P` where `M`, `N`, and `P` are integers that form the next
appropriate [semantic version][semver] for release.  For example:

```sh
$ git tag -a 1.2.3
```

The Travis CI build (see badge at the top of this page) will
automatically create a GitHub release using the tag name for the
release and the comment provided on the annotated tag as the contents
of the release notes.  It will also automatically upload the needed
artifacts.

[semver]: http://semver.org 
