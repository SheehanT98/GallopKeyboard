#!/usr/bin/env node
const {
	syncJobBranchWithMaster,
	syncAllOpenJobBranches,
	updateReadmeDashboard,
} = require('./devteam-lib.cjs');

function printHelp() {
	console.log(`Usage: node scripts/devteam-sync.cjs <job-id> [options]
       node scripts/devteam-sync.cjs --all-open [options]

Merge origin/master into a devteam feature branch and push.
Auto-resolves trivial devteam/README.md merge conflicts by regenerating the dashboard.

Options:
  --no-push   Merge locally only; do not push
  --json      Print result as JSON
  --help      Show this help

Examples:
  node scripts/devteam-sync.cjs job-016
  node scripts/devteam-sync.cjs --all-open
`);
}

function main() {
	const args = process.argv.slice(2);
	if (args.includes('--help') || args.includes('-h')) {
		printHelp();
		process.exit(0);
	}

	const json = args.includes('--json');
	const push = !args.includes('--no-push');
	const allOpen = args.includes('--all-open');
	const jobId = args.find((arg) => /^job-\d{3}$/.test(arg));

	if (!allOpen && !jobId) {
		console.error('Error: job-id (e.g. job-016) or --all-open is required.');
		printHelp();
		process.exit(1);
	}

	try {
		const root = process.cwd();
		const results = allOpen
			? syncAllOpenJobBranches(root, { push })
			: [syncJobBranchWithMaster(root, jobId, { push })];

		updateReadmeDashboard(root);

		if (json) {
			console.log(JSON.stringify(results, null, 2));
		} else {
			for (const result of results) {
				if (result.synced) {
					const readmeNote = result.readmeRegenerated ? ' (README regenerated)' : '';
					console.log(`Synced ${result.jobId} on ${result.branch}${readmeNote}`);
				} else {
					console.log(`Failed ${result.jobId}: ${result.error || 'unknown error'}`);
				}
			}
		}
		process.exit(0);
	} catch (error) {
		console.error(`Error: ${error.message}`);
		process.exit(1);
	}
}

main();
