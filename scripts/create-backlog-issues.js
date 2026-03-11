#!/usr/bin/env node

// Creates GitHub issues for every docs/backlog/*.md file that does not
// already have a corresponding issue.  Designed to run inside a GitHub
// Actions workflow via `actions/github-script`, but the core logic is
// importable so it can also be tested or triggered from other pipelines.
//
// Usage from a workflow step (actions/github-script@v7):
//
//   const script = require('./scripts/create-backlog-issues.js');
//   await script({ github, context, core, fs: require('fs'), path: require('path') });

/**
 * Mapping of backlog markdown files → issue metadata.
 * Add new entries here when new backlog items are created.
 */
const BACKLOG_ITEMS = [
  { file: '11-hashicorp-vault-integration.md',     title: 'Feature: HashiCorp Vault Integration',                           labels: ['enhancement', 'integration', 'security'] },
  { file: '12-opa-integration.md',                 title: 'Feature: OPA (Open Policy Agent) Integration',                    labels: ['enhancement', 'integration', 'policy'] },
  { file: '13-jira-confluence-integration.md',     title: 'Feature: Jira & Confluence Integration',                          labels: ['enhancement', 'integration'] },
  { file: '14-grafana-dashboard-integration.md',   title: 'Feature: Grafana Dashboard & Prometheus Metrics Integration',      labels: ['enhancement', 'integration', 'monitoring'] },
  { file: '15-regulatory-compliance-framework.md', title: 'Feature: Regulatory Compliance Framework (SOX, PCI-DSS, GDPR)',   labels: ['enhancement', 'compliance', 'governance'] },
  { file: '16-security-scan-integration.md',       title: 'Feature: Security Scan Integration (OWASP ZAP, Snyk, Trivy)',     labels: ['enhancement', 'security'] },
  { file: '17-continuous-evidence-collection.md',   title: 'Feature: Continuous Evidence Collection Pipeline',                labels: ['enhancement', 'pipeline', 'automation'] },
  { file: '18-immutable-ledger-support.md',         title: 'Feature: Immutable Ledger Support (Hash-Chain & AWS QLDB)',       labels: ['enhancement', 'infrastructure', 'audit-log'] },
];

const BACKLOG_DIR = 'docs/backlog';
const NEW_STATUS_MARKER = 'No existing GitHub issue';

/**
 * Ensure a single label exists; create it if missing.
 */
async function ensureLabel(github, owner, repo, label, core) {
  try {
    await github.rest.issues.getLabel({ owner, repo, name: label });
  } catch (e) {
    // Only attempt to create the label if it truly does not exist (404).
    if (e && e.status === 404) {
      try {
        await github.rest.issues.createLabel({
          owner,
          repo,
          name: label,
          color: '0e8a16',
          description: 'Auto-created backlog label',
        });
        core.info(`🏷️  Created label "${label}"`);
      } catch (createError) {
        core.warning(`⚠️  Failed to create label "${label}": ${createError.message}`);
      }
    } else {
      core.warning(
        `⚠️  Failed to verify existence of label "${label}" (status: ${e && e.status}): ${e && e.message}`
      );
    }
  }
}

/**
 * Check whether an issue with the given title already exists in the repo.
 * Returns the issue number if found, or null otherwise.
 */
async function findExistingIssue(github, owner, repo, title) {
  const { data } = await github.rest.search.issuesAndPullRequests({
    q: `repo:${owner}/${repo} is:issue in:title "${title}"`,
  });
  return data.total_count > 0 ? data.items[0].number : null;
}

/**
 * Update the backlog file's status line to reference the given issue.
 * Throws if the expected status marker is not found.
 */
function updateBacklogStatus(fs, filePath, content, issueNumber, issueUrl, core) {
  const updated = content.replace(
    /> \*\*Status:\*\* 🆕 New — No existing GitHub issue/,
    `> **Status:** ✅ Existing — [GitHub Issue #${issueNumber}](${issueUrl})`
  );
  if (updated === content) {
    const message = `Failed to update status line in "${filePath}" for issue #${issueNumber}: expected status marker not found.`;
    core.error(message);
    throw new Error(message);
  }
  fs.writeFileSync(filePath, updated);
}

/**
 * Main entry point — scans backlog files, creates missing issues, and
 * updates each file's status line.
 *
 * @param {object} opts
 * @param {object} opts.github  - Octokit client (from actions/github-script)
 * @param {object} opts.context - GitHub Actions context
 * @param {object} opts.core    - @actions/core for logging / summary
 * @param {object} opts.fs      - Node fs module
 * @param {object} opts.path    - Node path module
 * @returns {{ created: Array, skipped: Array }}
 */
async function createBacklogIssues({ github, context, core, fs, path }) {
  const owner = context.repo.owner;
  const repo  = context.repo.repo;

  const created = [];
  const skipped = [];

  for (const item of BACKLOG_ITEMS) {
    const filePath = path.join(BACKLOG_DIR, item.file);
    const content  = fs.readFileSync(filePath, 'utf8');

    // Skip files that already reference a GitHub issue
    if (!content.includes(NEW_STATUS_MARKER)) {
      skipped.push(item.title);
      core.info(`⏭️  Skipped "${item.title}" — already has an issue`);
      continue;
    }

    // If an issue with this title already exists on GitHub, update the backlog file and skip
    const existingNumber = await findExistingIssue(github, owner, repo, item.title);
    if (existingNumber) {
      const issueUrl = `https://github.com/${owner}/${repo}/issues/${existingNumber}`;
      updateBacklogStatus(fs, filePath, content, existingNumber, issueUrl, core);
      skipped.push(item.title);
      core.info(`⏭️  Skipped "${item.title}" — issue already exists (#${existingNumber}), updated backlog file`);
      continue;
    }

    // Ensure labels exist before creating the issue
    for (const label of item.labels) {
      await ensureLabel(github, owner, repo, label, core);
    }

    // Create the issue
    const { data: issue } = await github.rest.issues.create({
      owner,
      repo,
      title: item.title,
      body: content,
      labels: item.labels,
    });

    core.info(`✅ Created issue #${issue.number}: ${item.title}`);
    created.push({ ...item, number: issue.number });

    // Update the backlog file status line
    const issueUrl = `https://github.com/${owner}/${repo}/issues/${issue.number}`;
    updateBacklogStatus(fs, filePath, content, issue.number, issueUrl, core);
  }

  // Write GitHub Actions job summary
  core.summary
    .addHeading('Backlog Issues Summary')
    .addRaw(`**Created:** ${created.length} issues\n\n`)
    .addRaw(`**Skipped:** ${skipped.length} items (already had issues)\n\n`);
  if (created.length > 0) {
    core.summary.addTable([
      [{ data: 'Issue', header: true }, { data: 'Title', header: true }],
      ...created.map(i => [`#${i.number}`, i.title]),
    ]);
  }
  await core.summary.write();

  return { created, skipped };
}

module.exports = createBacklogIssues;
