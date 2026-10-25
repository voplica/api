"""unittest: the shape of .github/workflows/python.yml (clients/typescript/test/workflow.test.mjs is the precedent): it
runs on a version tag and by hand only; the run token reads only; the publish job alone holds an OIDC identity, in the
environment `pypi`, and publishes by PyPI Trusted Publishing with a publish action pinned by commit — no token secret
and no App token anywhere; a manual run builds and tests only, with the pinned tools; the oldest Python is tested
with the oldest runtime; the smoke installs the published version from PyPI into an empty venv. Read line by line,
as the precedent does: the file is the house's own plain YAML (two-space indentation, no anchors, no flow
mappings)."""
import re
import unittest

import support

LINES = support.WORKFLOW.read_text(encoding="utf-8").splitlines()


def indentation(line):
    return len(line) - len(line.lstrip(" "))


def code(line):
    """A line without its comment ('' for a blank or a comment line)."""
    return "" if line.strip().startswith("#") else re.sub(r"\s+#.*$", "", line).rstrip()


def block(lines, key, indent):
    """The lines under the one `key:` at `indent` spaces, up to the next line indented that much or less."""
    head = " " * indent + key + ":"
    starts = [i for i, line in enumerate(lines) if code(line) == head or code(line).startswith(head + " ")]
    if len(starts) != 1:
        raise AssertionError(f"{len(starts)} `{key}:` at {indent} spaces")
    body = []
    for line in lines[starts[0] + 1 :]:
        if code(line) and indentation(line) <= indent:
            break
        body.append(line)
    return body


def value(lines, key, indent):
    """The inline value of the one `key:` at `indent` spaces ('' when the key holds a block)."""
    head = " " * indent + key + ":"
    found = [code(line)[len(head) :].strip() for line in lines if code(line).split(":")[0] + ":" == head]
    if len(found) != 1:
        raise AssertionError(f"{len(found)} `{key}:` at {indent} spaces")
    return found[0]


def entries(lines, indent):
    """The code of the lines at exactly `indent` spaces, stripped."""
    return [code(line).strip() for line in lines if code(line) and indentation(line) == indent]


def jobs():
    return [entry[:-1] for entry in entries(block(LINES, "jobs", 0), 2)]


def job(name):
    return block(block(LINES, "jobs", 0), name, 2)


def uses(lines):
    return [match.group(1) for line in lines if (match := re.match(r"^\s*(?:-\s+)?uses:\s*(\S+)$", code(line)))]


def text(lines):
    return "\n".join(code(line) for line in lines if code(line))


class PythonWorkflow(unittest.TestCase):
    def test_it_runs_on_a_version_tag_and_by_hand_only(self):
        triggers = block(LINES, "on", 0)
        self.assertEqual(entries(triggers, 2), ["push:", "workflow_dispatch:"])
        self.assertEqual(entries(block(triggers, "push", 2), 4), ['tags: ["v*"]'])
        self.assertEqual(entries(block(triggers, "workflow_dispatch", 2), 4), [])

    def test_the_run_token_reads_only_and_only_the_publish_job_holds_an_identity(self):
        # Exactly two permissions keys, both blocks: the top level's and the publish job's (a job's `write-all`, or a
        # permissions block in any other job, is refused here before the grants are read).
        heads = [code(line) for line in LINES if re.match(r"^\s*permissions:", code(line))]
        self.assertEqual(heads, ["permissions:", "    permissions:"])
        self.assertEqual(entries(block(LINES, "permissions", 0), 2), ["contents: read"])
        self.assertEqual(entries(block(job("publish"), "permissions", 4), 6), ["id-token: write"])
        grants = [code(line).strip() for line in LINES if re.search(r":\s*write$", code(line))]
        self.assertEqual(grants, ["id-token: write"], "a write permission outside the publish job's identity")
        for name in jobs():
            if name != "publish":
                self.assertNotIn("id-token", text(job(name)), f"{name}: an OIDC identity")

    def test_the_publish_job_publishes_by_trusted_publishing_in_the_environment_pypi(self):
        publish = job("publish")
        self.assertEqual(value(publish, "environment", 4), "pypi")
        self.assertEqual(
            value(publish, "if", 4), "github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')"
        )
        needs = {need.strip() for need in value(publish, "needs", 4).strip("[]").split(",")}
        self.assertEqual(needs, {"build", "test"})
        actions = [ref for ref in uses(publish) if ref.startswith("pypa/gh-action-pypi-publish@")]
        self.assertEqual(len(actions), 1, "not one pypa/gh-action-pypi-publish step")
        self.assertRegex(actions[0], r"@[0-9a-f]{40}$")
        published = text(publish)
        self.assertRegex(published, r"(?m)^\s*skip-existing: true$")
        for key in ("password", "user", "username", "repository-url", "repository_url"):
            self.assertNotRegex(published, rf"(?m)^\s*{key}:", f"the publish step sets {key}")

    def test_no_token_secret_and_no_app_token(self):
        everything = text(LINES)
        self.assertNotIn("secrets.", everything)
        app_token = ("create-github-app-token", "app-id:", "client-id:", "private-key:", "permission-")
        for word in (*app_token, "github.token", "pull_request_target"):
            self.assertNotIn(word, everything)
        # Every checkout leaves the run token out of the working tree's git config.
        checkouts = [ref for ref in uses(LINES) if ref.startswith("actions/checkout@")]
        self.assertEqual(everything.count("persist-credentials: false"), len(checkouts))

    def test_every_action_is_pinned(self):
        for ref in uses(LINES):
            if ref.startswith("actions/"):
                self.assertRegex(ref, r"^actions/[a-z-]+@v\d+$")
            else:
                self.assertRegex(ref, r"^[\w.-]+/[\w.-]+@[0-9a-f]{40}$", "a third-party action not pinned by commit")

    def test_the_build_job_generates_builds_and_tests_and_refuses_a_foreign_tag(self):
        build = text(job("build"))
        for step in ("-r requirements.txt", "scripts/generate.py", "-m build", "-m unittest discover", '"v$version"'):
            self.assertIn(step, build)
        self.assertEqual(value(job("test"), "needs", 4), "build")
        self.assertEqual(value(job("smoke"), "needs", 4), "publish")
        for name in ("build", "test"):
            self.assertNotIn("if", [entry.split(":")[0] for entry in entries(job(name), 4)], f"{name} is conditional")

    def test_the_oldest_runtime_is_tested(self):
        self.assertIn('requires-python = ">=3.10"', support.PYPROJECT.read_text(encoding="utf-8"))
        # The matrix entries read as pairs: the oldest Python runs with the oldest runtime, not merely beside it.
        pairs = []
        for entry in (code(line).strip() for line in job("test")):
            if python := re.fullmatch(r'- python: "([^"]+)"', entry):
                pairs.append([python.group(1), None])
            elif (requirements := re.fullmatch(r"requirements: (\S+)", entry)) and pairs:
                pairs[-1][1] = requirements.group(1)
        self.assertIn(["3.10", "clients/python/requirements-floor.txt"], pairs)

    def test_the_smoke_installs_the_published_version_from_pypi_into_an_empty_venv(self):
        smoke = text(job("smoke"))
        self.assertIn("-m venv", smoke)
        self.assertRegex(smoke, r'-m pip install [^\n]*"voplica-api==\$version"')
        for option in ("--index-url", "--extra-index-url", "--find-links", "--no-index", " -i "):
            self.assertNotIn(option, smoke)
        self.assertIn("smoke.py", smoke)


if __name__ == "__main__":
    unittest.main()
