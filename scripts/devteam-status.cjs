#!/usr/bin/env node
const { buildStatusReport, autoArchiveMergedJobs } = require('./devteam-lib.cjs');

function printHelp() {
	console.log(`Usage: node scripts/devteam-status.cjs [options]

Show devteam v2 dashboard: active jobs (max 3), queue, awaiting review.

Options:
  --fetch     Fetch origin and auto-archive merged jobs first
  --json      Print as JSON
  --help      Show this help
`);
}

function main() {
	const args = process.argv.slice(2);
	if (args.includes('--help') || args.includes('-h')) {
		printHelp();
		process.exit(0);
	}

	const root = process.cwd();
	if (args.includes('--fetch')) {
		try {
			const { execSync } = require('child_process');
			execSync('git fetch origin', { cwd: root, stdio: 'ignore' });
		} catch {
			// offline ok
		}
		autoArchiveMergedJobs(root);
	}

	try {
		const report = buildStatusReport(root);
		if (args.includes('--json')) {
			console.log(JSON.stringify(report, null, 2));
		} else {
			console.log(report.dashboard);
			console.log('');
			console.log('## Ready for review');
			console.log(report.readyForReview);
		}
		process.exit(0);
	} catch (error) {
		console.error(`Error: ${error.message}`);
		process.exit(1);
	}
}

main();
